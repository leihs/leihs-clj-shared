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

(defn persist-request [txid request]
  (jdbc/insert! @ds/ds :audited_requests
                {:txid txid
                 :http_uid (-> request :headers (get "http-uid"))
                 :url (-> request :uri)
                 :user_id (-> request :authenticated-entity :id)
                 :method (-> request :request-method str)}))

(defn persist-response [txid response]
  (jdbc/insert! @ds/ds :audited_responses
                {:txid txid
                 :status (:status response)}))

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
