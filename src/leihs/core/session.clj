(ns leihs.core.session
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.core :refer [str keyword presence presence!]]
    [leihs.core.sql :as sql]
    [leihs.core.constants :refer [USER_SESSION_COOKIE_NAME]]

    [pandect.core]
    [logbug.catcher :as catcher]
    [clojure.java.jdbc :as jdbc]

    [logbug.debug :as debug]
    )
  (:import
    [java.util UUID]
    ))

(def user-select 
  [:users.*    
   [:users.id :user_id]
   [(-> (sql/select :%count.*)
        (sql/from :contracts)
        (sql/merge-where [:= :contracts.user_id :users.id]))
    :contracts_count]
   [(-> (sql/select :%count.*)
        (sql/from :access_rights)
        (sql/merge-where [:= :access_rights.user_id :users.id]))
    :inventory_pool_roles_count]])

(defn user-with-valid-session-query [session-token]
  (-> (apply sql/select user-select) 
      (sql/merge-select 
        [:user_sessions.id :user_session_id]
        [:user_sessions.created_at :user_session_created_at])
      (sql/from :users)
      (sql/merge-join :user_sessions [:= :users.id :user_id])
      (sql/merge-join :settings [:= :settings.id 0])
      (sql/merge-where (sql/call
                         := :user_sessions.token_hash
                         (sql/call :encode
                                   (sql/call :digest session-token "sha256")
                                   "hex")))
      (sql/merge-where
        (sql/raw (str "now() < user_sessions.created_at + "
                      " settings.sessions_max_lifetime_secs * interval '1 second'")))
      (sql/merge-where [:= :account_enabled true])
      sql/format))

(defn authenticated-user-entity [session-token tx]
  (when-let [user (->>
                    (user-with-valid-session-query session-token)
                    (jdbc/query tx) first)]
    (assoc user
           :authentication-method :session
           :scope_read true
           :scope_write true
           :scope_admin_read (:is_admin user)
           :scope_admin_write (:is_admin user))))

(defn session-token [request]
  (some-> request :cookies
      (get USER_SESSION_COOKIE_NAME nil) :value))

(defn- authenticate [request]
    (catcher/snatch
      {:level :warn
       :return-expr request}
      (if-let [user (some-> request
                            session-token
                            (authenticated-user-entity (:tx request)))]
        (assoc request :authenticated-entity user)
        request)))

(defn wrap-authenticate [handler]
  (fn [request]
    (-> request authenticate handler)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn create-user-session
  [user {:as request tx :tx settings :settings}]
  "Create and returns the user_session. The map includes additionally
  the original token to be usee as the value of the session cookie."
  (when (:sessions_force_uniqueness settings)
    (jdbc/delete! tx :user_sessions ["user_id = ?" (:id user)]))
  (let [token (str (UUID/randomUUID))
        token-hash (pandect.core/sha256 token)
        user-session (->> {:user_id (:id user)
                           :token_hash token-hash
                           :meta_data  {:user_agent (get-in request [:headers "user-agent"])
                                        :remote_addr (get-in request [:remote-addr])}}
                          (jdbc/insert! tx :user_sessions)
                          first)]
    (assoc user-session :token token)))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
