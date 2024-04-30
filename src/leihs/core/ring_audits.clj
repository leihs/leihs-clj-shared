(ns leihs.core.ring-audits
  (:refer-clojure :exclude [str keyword])
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [leihs.core.constants :as constants]
   [leihs.core.core :refer [str]]
   [leihs.core.db :as db]
   [leihs.core.graphql :as graphql]
   [leihs.core.ring-exception :as ring-exception]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :refer [insert! query] :rename {query jdbc-query,
                                                  insert! jdbc-insert!}]))

(defn txid [tx]
  (->> ["SELECT txid() AS txid"]
       (jdbc-query tx)
       first :txid))

(defn persist-request [txid request]
  (jdbc-insert! (db/get-ds) :audited_requests
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
                      [:raw " audited_changes.table_name = 'user_sessions'"]
                      [:= :user_sessions.id [:cast :audited_changes.pkey :uuid]]])
           (sql/where [:= :audited_requests.txid txid])
           sql-format)
       (jdbc/execute! tx)))

(defn persist-response [txid response]
  (jdbc-insert! (db/get-ds) :audited_responses
                {:txid txid
                 :status (:status response)}))

(defn wrap
  ([handler]
   (fn [request]
     (wrap handler request)))
  ([handler {handler-key :handler-key
             method :request-method
             tx :tx :as request}]
   (letfn [(audited-handler [request]
             (let [txid (txid tx)]
               (persist-request txid request)
               (let [response (try (handler request)
                                   (catch Exception e
                                     (persist-response
                                      txid (ring-exception/exception-response e))
                                     (throw e)))]
                 (persist-response txid response)
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
