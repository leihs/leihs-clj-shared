(ns leihs.core.db
  (:refer-clojure :exclude [str keyword])
  (:require
   [clj-yaml.core :as yaml]
   [clojure.java.jdbc :as jdbc]
   [cuerdas.core :as string :refer [snake kebab upper human]]
   [environ.core :refer [env]]
   [hikari-cp.core :as hikari]
   [leihs.core.constants :refer [HTTP_UNSAFE_METHODS]]
   [leihs.core.core :refer [keyword str presence]]
   [leihs.core.db.type-conversion]
   [leihs.core.graphql :as graphql]
   [leihs.core.ring-exception :refer [get-cause]]
   [leihs.core.sql :as sql]
   [leihs.core.sql2]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
   [logbug.ring :refer [wrap-handler-with-logging]]
   [logbug.thrown :as thrown]
   [next.jdbc :as jdbc-next]
   [next.jdbc.connection]
   [next.jdbc.result-set]
   [pg-types.all]
   [ring.util.codec]
   [taoensso.timbre :refer [debug info warn error spy]])
  (:import
   [com.codahale.metrics MetricRegistry]
   [com.zaxxer.hikari HikariDataSource]
   java.net.URI))

;;; CLI ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn long-opt-for-key [k]
  (str "--" (kebab k) " " (-> k snake upper)))

(def db-name-key :db-name)
(def db-port-key :db-port)
(def db-host-key :db-host)
(def db-user-key :db-user)
(def db-password-key :db-password)
(def db-min-pool-size-key :db-min-pool-size)
(def db-max-pool-size-key :db-max-pool-size)
(def options-keys [db-name-key db-port-key db-host-key
                   db-user-key db-password-key
                   db-min-pool-size-key db-max-pool-size-key])

(def cli-options
  [[nil (long-opt-for-key db-name-key) "Database name, falls back to PGDATABASE | leihs"
    :default (or (some-> db-name-key env) "leihs")]
   [nil (long-opt-for-key db-port-key) "Database port, falls back to PGPORT or 5432"
    :default (or (some-> db-port-key env Integer/parseInt)
                 (some-> :pgport env Integer/parseInt)
                 5432)
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be an integer between 0 and 65536"]]
   [nil (long-opt-for-key db-host-key) "Database host, falls back to PGHOST | localhost"
    :default (or (some-> db-host-key env)
                 (some-> :pghost env)
                 "localhost")]
   [nil (long-opt-for-key db-user-key) "Database user, falls back to PGUSER | 'leihs'"
    :default (yaml/parse-string (or (some-> db-user-key env)
                                    (some-> :pguser env)
                                    "leihs"))
    :parse-fn yaml/parse-string]
   [nil (long-opt-for-key db-password-key) "Database password, falls back to PGPASSWORD |'leihs'"
    :default (yaml/parse-string (or (some-> db-password-key env)
                                    (some-> :pgpassword env)
                                    "leihs"))
    :parse-fn yaml/parse-string]
   [nil (long-opt-for-key db-min-pool-size-key)
    :default (or (some-> db-min-pool-size-key env Integer/parseInt)
                 2)
    :parse-fn #(Integer/parseInt %)]
   [nil (long-opt-for-key db-max-pool-size-key)
    :default (or (some-> db-max-pool-size-key env Integer/parseInt)
                 16)
    :parse-fn #(Integer/parseInt %)]])

;;; ds ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ds* (atom nil))

(defn get-ds [] @ds*)

(defonce metric-registry* (atom nil))

(defn Timer->map [t]
  {:count (.getCount t)
   :mean-reate (.getMeanRate t)
   :one-minute-rate (.getOneMinuteRate t)
   :five-minute-rate (.getFiveMinuteRate t)
   :fifteen-minute-rate (.getFifteenMinuteRate t)})

(defn status []
  {:gauges (->>
            @metric-registry* .getGauges
            (map (fn [[n g]] [n (.getValue g)]))
            (into {}))
   :timers (->> @metric-registry* .getTimers
                (map (fn [[n t]] [n (Timer->map t)]))
                (into {}))})

(defn close []
  (when @ds*
    (do
      (info "Closing db pool ...")
      (-> @ds* :datasource hikari/close-datasource)
      (reset! ds* nil)
      (info "Closing db pool done."))))

(defn init-ds [db-options health-check-registry]
  (reset! metric-registry* (MetricRegistry.))
  (reset!
   ds*
   {:datasource
    (hikari/make-datasource
     {:auto-commit        true
      :read-only          false
      :connection-timeout 30000
      :validation-timeout 5000
      :idle-timeout       (* 1 60 1000) ; 1 minute
      :max-lifetime       (* 1 60 60 1000) ; 1 hour
      :minimum-idle       (get db-options db-min-pool-size-key)
      :maximum-pool-size  (get db-options db-max-pool-size-key)
      :pool-name          "db-pool"
      :adapter            "postgresql"
      :username           (get db-options db-user-key)
      :password           (get db-options db-password-key)
      :database-name      (get db-options db-name-key)
      :server-name        (get db-options db-host-key)
      :port-number        (get db-options db-port-key)
      :register-mbeans    false
      :metric-registry @metric-registry*
      :health-check-registry health-check-registry})}))

;;; next-ds ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def builder-fn-options
  {:builder-fn next.jdbc.result-set/as-unqualified-lower-maps})

(defonce ds-next* (atom nil))

(defn get-ds-next [] @ds-next*)

(defn close-next []
  (when @ds-next*
    (do
      (info "Closing db pool ...")
      (.close ^HikariDataSource (:connectable @ds-next*))
      (reset! ds-next* nil)
      (info "Closing db pool done."))))

(defn init-ds-next [db-options]
  (try (let [params {:dbtype "postgres"
                     :dbname (get db-options db-name-key)
                     :username (get db-options db-user-key)
                     :password (get db-options db-password-key)
                     :host (get db-options db-host-key)
                     :port (get db-options db-port-key)
                     :maximumPoolSize  (get db-options db-max-pool-size-key)
                     :minimumIdle (get db-options db-min-pool-size-key)
                     :autoCommit true
                     :connectionTimeout 30000
                     :validationTimeout 5000
                     :idleTimeout (* 1 60 1000) ; 1 minute
                     :maxLifetime (* 1 60 60 1000)}
             ds-next (next.jdbc.connection/->pool
                      HikariDataSource params)]
         ;; this code initializes the pool and performs a validation check:
         (.close (jdbc-next/get-connection ds-next))
         (reset! ds-next* (jdbc-next/with-options ds-next builder-fn-options))
         @ds-next*)
       (catch Throwable th
         (error th)
         (throw th))))

;;; wrap ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn wrap-tx [handler]
  (fn [request]
    (let [handler-key (:handler-key request)]
      (if (or (and (= handler-key :graphql) (graphql/mutation? request))
              (and (not= handler-key :graphql) (HTTP_UNSAFE_METHODS (:request-method request)))
              (= handler-key :external-authentication-sign-in))
        (jdbc/with-db-transaction [tx (get-ds)]
          (jdbc-next/with-transaction+options [tx-next (get-ds-next)]
            (letfn [(rollback-both-tx! []
                      (jdbc/db-set-rollback-only! tx)
                      (.rollback (:connectable tx-next)))]
              (try (let [resp (-> request
                                  (assoc :tx tx)
                                  (assoc :tx-next tx-next)
                                  handler)
                         resp-body (:body resp)
                         resp-status (:status resp)]
                     (cond (and (graphql/mutation? request) (:graphql-error resp))
                           (do (warn "Rolling back transaction because of graphql error " (:errors resp-body))
                               (rollback-both-tx!))
                           (some-> resp-status (>= 400))
                           (do (warn "Rolling back transaction because error status " resp-status)
                               (rollback-both-tx!)))
                     resp)
                   (catch Throwable th
                     (warn "Rolling back transaction because of " (.getMessage th))
                     (rollback-both-tx!)
                     (throw th))))))
        (-> request
            (assoc :tx (get-ds))
            (assoc :tx-next (get-ds-next))
            handler)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init
  ([options]
   (init options nil))
  ([options health-check-registry]
   (let [db-options (select-keys options options-keys)]
     (info "Initializing db " db-options)
     (init-ds db-options health-check-registry)
     (info "Initialized db " @ds*)
     (info "Initializing ds-next " db-options)
     (init-ds-next db-options)
     (info "Initialized ds-next " @ds-next*))))
