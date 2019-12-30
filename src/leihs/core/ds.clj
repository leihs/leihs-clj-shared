(ns leihs.core.ds
  (:refer-clojure :exclude [str keyword])
  (:require
    [clojure.java.jdbc :as jdbc]
    [hikari-cp.core :as hikari]
    pg-types.all
    ring.util.codec
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.ring-exception :refer [get-cause]]
    [leihs.core.sql :as sql]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    )
  (:import
    java.net.URI
    [com.codahale.metrics MetricRegistry]
    ))

;;; status ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce metric-registry* (atom nil))

(defn Timer->map [t]
  {:count (.getCount t)
   :mean-reate (.getMeanRate t)
   :one-minute-rate (.getOneMinuteRate t)
   :five-minute-rate (.getFiveMinuteRate t)
   :fifteen-minute-rate (.getFifteenMinuteRate t)
   })

(defn status []
  {:gauges (->>
             @metric-registry* .getGauges
             (map (fn [[n g]] [n (.getValue g)]))
             (into {}))
   :timers (->> @metric-registry* .getTimers
                (map (fn [[n t]] [n (Timer->map t)]))
                (into {}))})

(defonce ds (atom nil))
(defn get-ds [] @ds)

(defn wrap-tx [handler]
  (fn [request]
    (jdbc/with-db-transaction [tx @ds]
      (try
        (let [resp (handler (assoc request :tx tx))]
          (when-let [status (:status resp)]
            (when (>= status 400 )
              (logging/warn "Rolling back transaction because error status " status)
              (jdbc/db-set-rollback-only! tx)))
          resp)
        (catch Throwable th
          (logging/warn "Rolling back transaction because of " (.getMessage th))
          (-> th get-cause logging/debug)
          (jdbc/db-set-rollback-only! tx)
          (throw th))))))

(defn wrap-after-tx [handler]
  (fn [request]
    (let [response (handler request)]
      (doseq [hook (:after-tx response)] (hook))
      response)))

(defn check-pending-migrations [ds]
  (let [run-versions (-> (sql/select :version)
                         (sql/from :schema_migrations)
                         sql/format
                         (->> (jdbc/query ds)
                              (map #(-> % :version Integer.))
                              (into #{})))
        migrations-dir "database/db/migrate"
        files-versions (-> migrations-dir
                           clojure.java.io/file
                           file-seq
                           (->> (filter #(and (.isFile %)
                                              (= (.getParent %) migrations-dir)))
                                (map (fn [f]
                                       (-> f 
                                           .getName
                                           (clojure.string/split #"_")
                                           first
                                           Integer.)))
                                (into #{})))
        pending-versions (clojure.set/difference files-versions run-versions)]
    (if-not (empty? pending-versions)
      (throw (Exception. "pending migrations!")))))

(defn close []
  (when @ds
    (do
      (logging/info "Closing db pool ...")
      (-> @ds :datasource hikari/close-datasource)
      (reset! ds nil)
      (logging/info "Closing db pool done."))))

(defn init [params health-check-registry]
  (close)
  (reset! metric-registry* (MetricRegistry.))
  (logging/info "Initializing db pool " params " ..." )
  (reset!
    ds
    {:datasource
     (hikari/make-datasource
       {:auto-commit        true
        :read-only          false
        :connection-timeout 30000
        :validation-timeout 5000
        :idle-timeout       (* 1 60 1000) ; 1 minute
        :max-lifetime       (* 1 60 60 1000) ; 1 hour
        :minimum-idle       (-> params :min-pool-size presence (or 2))
        :maximum-pool-size  (-> params :max-pool-size presence (or 16))
        :pool-name          "db-pool"
        :adapter            "postgresql"
        :username           (:username params)
        :password           (:password params)
        :database-name      (:database params)
        :server-name        (:host params)
        :port-number        (:port params)
        :register-mbeans    false
        :metric-registry @metric-registry*
        :health-check-registry health-check-registry})})
  (check-pending-migrations @ds)
  (logging/info "Initializing db pool done.")
  @ds)

;;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
