(ns leihs.core.ring-audits
  (:refer-clojure :exclude [str keyword])
  (:require [clj-logging-config.log4j :as logging-config]
            [leihs.core.core :refer [keyword str presence]])
  (:require
    [leihs.core.constants :as constants]
    [leihs.core.ds :as ds]
    [leihs.core.ring-exception :as ring-exception]

    [clojure.java.jdbc :as jdbc]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]))

(defn txid [tx]
  (->> ["SELECT txid() AS txid"]
       (jdbc/query tx)
       first :txid))

(defn deep-map-clean! [m]
  (->> m
       (map (fn [[k v]]
              (cond
                (= k :password) [k "********"]
                (= k :token_secret) [k "********"]
                (map? v)[k (deep-map-clean! v)]
                (boolean? v) [k v]
                (keyword? v) [k v]
                (nil? v) [k v]
                (number? v) [k v]
                (string? v) [k v]
                :else [k (pr-str v)])))
       (into {})))

(defn clean-keys! [request]
  (dissoc request
          :settings
          :tx ))

(defn persist-request [txid request]
  (jdbc/insert! @ds/ds :audited_requests
                {:txid txid
                 :url (-> request :uri)
                 :user_id (-> request :authenticated-entity :id)
                 :method (-> request :request-method str)
                 :data (-> request clean-keys! deep-map-clean!)
                 }))

(defn persist-response [txid response]
  (jdbc/insert! @ds/ds :audited_responses
                {:txid txid
                 :status (:status response)
                 :data (-> response deep-map-clean!)
                 }))

(defn wrap
  ([handler]
   (fn [request]
     (wrap handler request)))
  ([handler request]
   (if-not ((-> request :request-method) constants/HTTP_UNSAVE_METHODS)
     (handler request)
     (let [txid (txid (:tx request))]
       (persist-request txid request)
       (let [response (try (handler request)
                           (catch Exception e
                             (ring-exception/exception-response e)))]
         (persist-response txid response)
         response)))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
