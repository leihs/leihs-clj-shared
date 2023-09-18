(ns leihs.core.ring-audits
  (:require
    [clojure.java.jdbc :as jdbc]
    [leihs.core.constants :as constants]
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.db :as db]
    [leihs.core.graphql :as graphql]
    [leihs.core.ring-exception :as ring-exception]
    [leihs.core.sql :as sql]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    [next.jdbc :as jdbc-next]
    [next.jdbc.sql :refer [query] :rename {query jdbc-next-query}]
    [taoensso.timbre :refer [error warn info debug spy]]
    )
  (:refer-clojure :exclude [str keyword]))

(defn txid [tx]
  (->> ["SELECT txid() AS txid"]
       (jdbc/query tx)
       first :txid))


(defn tx2id [tx-next]
  (->> ["SELECT txid() AS tx2id"]
       (jdbc-next-query tx-next)
       first spy :tx2id))

(defn persist-request [txid tx2id request]
  (jdbc/insert! (db/get-ds) :audited_requests
                {:txid txid
                 :tx2id tx2id
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

(defn persist-response [txid tx2id response]
  (jdbc/insert! (db/get-ds) :audited_responses
                {:txid txid
                 :tx2id tx2id
                 :status (:status response)}))

(defn wrap
  ([handler]
   (fn [request]
     (wrap handler request)))
  ([handler {handler-key :handler-key
             method :request-method
             tx :tx tx-next :tx-next :as request}]
   (letfn [(audited-handler [request]
             (let [txid (txid tx)
                   tx2id (tx2id tx-next)]
               (persist-request txid tx2id request)
               (let [response (try (handler request)
                                   (catch Exception e
                                     (persist-response
                                       txid tx2id
                                       (ring-exception/exception-response e))
                                     (throw e)))]
                 (persist-response txid tx2id response)
                 (when (#{:external-authentication-sign-in :sign-in} handler-key)
                   (update-request-user-id-from-session txid tx))
                 response)))]
     (cond
       (and (constants/HTTP_SAVE_METHODS method)
            (not= handler-key :external-authentication-sign-in))
       (handler request)

       (and (= handler-key :graphql)
            (not (graphql/to-be-audited? request)))
       (handler request)

       :else (audited-handler request)))))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
