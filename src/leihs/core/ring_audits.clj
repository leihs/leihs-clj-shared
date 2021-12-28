(ns leihs.core.ring-audits
  (:refer-clojure :exclude [str keyword])
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [leihs.core.constants :as constants]
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.db :as db]
    [leihs.core.ring-exception :as ring-exception]
    [leihs.core.sql :as sql]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]))

(defn txid [tx]
  (->> ["SELECT txid() AS txid"]
       (jdbc/query tx)
       first :txid))

(defn persist-request [txid request]
  (jdbc/insert! (db/get-ds) :audited_requests
                {:txid txid
                 :http_uid (-> request :headers (get "http-uid"))
                 :path (-> request :uri)
                 :user_id (-> request :authenticated-entity :user_id)
                 :method (-> request :request-method str)}))

(defn update-request-user-id-from-session [txid tx]
  "A note to the tx: this one must be run in the transaction
  otherwise it will not see the row in the user_sessions "
  (->> (-> (sql/update :audited_requests)
           (sql/set {:user_id :user_sessions.user_id})
           (sql/from :user_sessions)
           (sql/join :audited_changes
                     [:and
                      [:= :audited_changes.txid txid]
                      (sql/raw " audited_changes.table_name = 'user_sessions'")
                      [:= :user_sessions.id (sql/call :cast :audited_changes.pkey :uuid)]])
           (sql/merge-where [:= :audited_requests.txid txid])
           sql/format)
       (jdbc/execute! tx)))

(defn persist-response [txid response]
  (jdbc/insert! (db/get-ds) :audited_responses
                {:txid txid
                 :status (:status response)}))

(defn wrap
  ([handler]
   (fn [request]
     (wrap handler request)))
  ([handler {handler-key :handler-key
             method :request-method
             tx :tx :as request}]
   (if-not (or (constants/HTTP_UNSAVE_METHODS method)
               (= handler-key :external-authentication-sign-in))
     (handler request)
     (let [txid (txid (:tx request))]
       (persist-request txid request)
       (let [response (try (handler request)
                           (catch Exception e
                             (ring-exception/exception-response e)))]
         (persist-response txid response)
         (when (#{:external-authentication-sign-in :sign-in} handler-key)
           (update-request-user-id-from-session txid tx))
         response)))))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
