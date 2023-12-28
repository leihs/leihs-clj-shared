(ns leihs.core.auth.session
  (:refer-clojure :exclude [keyword str])
  (:require
    [cheshire.core :refer [generate-string] :rename
     {generate-string to-json}]
    [honey.sql :refer [format] :rename {format sql-format}]
    [honey.sql.helpers :as sql]
    [leihs.core.auth.shared :refer [access-rights]]
    [leihs.core.constants :refer [USER_SESSION_COOKIE_NAME]]
    [leihs.core.core :refer [str]]
    [leihs.core.utils :refer [my-cast]]
    ;; all needed imports
    [logbug.catcher :as catcher]
    [next.jdbc :as jdbc-next]
    [pandect.core]
    )
  (:import
    [java.util UUID]
    ))

(def user-select
  [:users.email
   :users.firstname
   :users.id
   :users.is_admin
   :users.language_locale
   :users.lastname
   :users.login
   :users.org_id
   :users.is_system_admin
   [:users.id :user_id]
   [(-> (sql/select :%count.*)
        (sql/from :contracts)
        (sql/where [:= :contracts.user_id :users.id]))
    :contracts_count]
   [(-> (sql/select :%count.*)
        (sql/from :access_rights)
        (sql/where [:= :access_rights.user_id :users.id]))
    :inventory_pool_roles_count]])

(defn user-with-valid-session-query [session-token]
  (-> (apply sql/select user-select)
      (sql/select
        [:user_sessions.id :user_session_id]
        [:user_sessions.created_at :user_session_created_at]
        [:authentication_systems.external_sign_out_url :external_sign_out_url])
      (sql/from :users)
      (sql/join :user_sessions [:= :users.id :user_id])
      (sql/join :authentication_systems
                [:= :authentication_systems.id :user_sessions.authentication_system_id])
      (sql/join :system_and_security_settings [:= :system_and_security_settings.id 0])
      (sql/where [:= :user_sessions.token_hash [[:encode [[:digest session-token "sha256"]] "hex"]]])
      (sql/where [:raw (str "now() < user_sessions.created_at + "
                            "system_and_security_settings.sessions_max_lifetime_secs * interval '1 second'")])
      (sql/where [:= :account_enabled true])
      sql-format))



(defn authenticated-user-entity [session-token {tx :tx-next :as request}]
  (when-let [user (let [query (user-with-valid-session-query session-token)]
                    (jdbc-next/execute-one! tx query))]
    (assoc user
           :authentication-method :session
           :access-rights (access-rights tx (:id user))
           :scope_read true
           :scope_write true
           :scope_admin_read (:is_admin user)
           :scope_admin_write (:is_admin user)
           :scope_system_admin_read (:is_system_admin user)
           :scope_system_admin_write (:is_system_admin user))))

(defn session-token [request]
  (some-> request :cookies
      (get USER_SESSION_COOKIE_NAME nil) :value))

(defn- authenticate [request]
    (catcher/snatch
      {:level :warn
       :return-expr request}
      (if-let [user (some-> request
                            session-token
                            (authenticated-user-entity request))]
        (assoc request :authenticated-entity user)
        request)))

(defn wrap-authenticate [handler]
  (fn [request]
    (-> request authenticate handler)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn create-user-session
  [user authentication_system_id {:as request tx :tx-next settings :settings}]
  "Create and returns the user_session. The map includes additionally
  the original token to be used as the value of the session cookie."
  (when (:sessions_force_uniqueness settings)
    (let [sql (-> (sql/delete-from :user_sessions)
                  (sql/where [:= :user_id (:id user)])
                  (sql/returning :*)
                  sql-format)
          ] (jdbc-next/execute-one! tx sql)))
  (let [token (str (UUID/randomUUID))
        token-hash (pandect.core/sha256 token)
        session-data {:user_id (:id user)
                      :token_hash token-hash
                      :authentication_system_id authentication_system_id
                      :meta_data {:user_agent (get-in request [:headers "user-agent"])
                                  :remote_addr (get-in request [:remote-addr])}}
        meta_data (-> (:meta_data session-data)
                      to-json)
        user-session-data (assoc session-data :meta_data meta_data)
        user-session (jdbc-next/execute-one! tx (-> (sql/insert-into :user_sessions)
                                                    (sql/values [(my-cast user-session-data)])
                                                    (sql/returning :*)
                                                    sql-format))]
    (assoc user-session :token token)))


;#### debug ###################################################################
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
