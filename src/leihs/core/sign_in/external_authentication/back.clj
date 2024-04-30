(ns leihs.core.sign-in.external-authentication.back
  (:refer-clojure :exclude [str keyword cond])
  (:require
   [better-cond.core :refer [cond]]
   [clojure.string :as str]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [leihs.core.auth.session :as session]
   [leihs.core.core :refer [presence str]]
   [leihs.core.paths :refer [path]]
   [leihs.core.redirects :refer [redirect-target]]
   [leihs.core.sign-in-sign-out.external-authentication
    :refer [create-signed-token unsign-external-token unsign-internal-token]]
   [leihs.core.sign-in-sign-out.shared
    :refer [auth-system-base-query-for-unique-id auth-system-user-query
            user-query-for-unique-id]]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]
   [ring.util.response :refer [redirect]]
   [taoensso.timbre :refer [debug]]))

(def skip-authorization-handler-keys
  "These keys needs the be added to the list of the skipped handler keys
  in each subapp in order to make the complete login process work in them."
  #{:external-authentication-request
    :external-authentication-sign-in})

(defn authentication-system-user-data
  [user-unique-id authentication-system-id tx]
  (when-let [authentication-system-and-user
             (->> (auth-system-user-query
                   user-unique-id authentication-system-id)
                  sql-format
                  (jdbc-query tx) first)]
    (merge authentication-system-and-user
           (->> (-> (sql/select :*)
                    (sql/from :authentication_systems_users)
                    (sql/where [:= :authentication_systems_users.authentication_system_id
                                (-> authentication-system-and-user :authentication_system :id)])
                    (sql/where [:= :authentication_systems_users.user_id
                                (-> authentication-system-and-user :user_id:id)])
                    sql-format)
                (jdbc-query tx) first))))

(defn authentication-system-user-data!
  [user-unique-id authentication-system-id tx]
  (or (authentication-system-user-data
       user-unique-id authentication-system-id tx)
      (throw (ex-info
              (str "External authentication system for existing user "
                   user-unique-id " not found or not enabled")
              {:status 500}))))

(defn claims! [user authentication-system settings return-to]
  {:email (when (:send_email authentication-system) (:email user))
   :login (when (:send_login authentication-system) (:login user))
   :org_id (when (:send_org_id authentication-system) (:org_id user))
   :server_base_url (:external_base_url settings)
   :return_to (presence return-to)
   :path (path :external-authentication-sign-in
               {:authentication-system-id (:id authentication-system)})})

(defn authentication-system [tx authentication-system-id]
  (->> (-> (sql/select :*)
           (sql/from :authentication_systems)
           (sql/where [:= :id authentication-system-id])
           (sql-format))
       (jdbc-query tx) first))

(defn user [tx user-unique-id]
  (->> (-> user-unique-id
           user-query-for-unique-id
           sql-format)
       (jdbc-query tx) first))

(defn ext-auth-system-token-url
  ([tx user-unique-id authentication-system-id settings]
   (ext-auth-system-token-url tx user-unique-id authentication-system-id settings nil))
  ([tx user-unique-id authentication-system-id settings return-to]
   (cond
     :let [authentication-system (authentication-system tx authentication-system-id)
           user (or (user tx user-unique-id)
                    {:email user-unique-id})
           claims (claims! user authentication-system settings return-to)
           token (create-signed-token claims authentication-system)]
     (str (:external_sign_in_url authentication-system) "?token=" token))))

(defn authentication-request
  [{tx :tx :as request
    settings :settings
    {authentication-system-id :authentication-system-id} :route-params
    {user-unique-id :user-unique-id return-to :return-to} :params}]
  (redirect (ext-auth-system-token-url tx
                                       user-unique-id
                                       authentication-system-id
                                       settings
                                       return-to)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn authentication-system! [id tx]
  (or (->> (-> (sql/select :authentication_systems.*)
               (sql/from :authentication_systems)
               (sql/where [:= :authentication_systems.id id])
               sql-format)
           (jdbc-query tx) first)
      (throw (ex-info "Authentication-System not found!" {:status 400}))))

(defn user-for-sign-in-token-query [sign-in-token authentication-system-id]
  (let [unique-ids [:email :login :org_id]
        unique-id (some sign-in-token unique-ids)
        base-query (auth-system-base-query-for-unique-id unique-id authentication-system-id)]
    (when-not unique-id
      (throw (ex-info
              "The sign-in token must at least submit one of email, org_id or login"
              {:status 400})))
    ; extending the base-query with the actual unique id(s) submitted makes this more stringent
    (as-> base-query query
      (if-let [email (:email sign-in-token)]
        (sql/where query [:= [:raw "lower(users.email)"] (str/lower-case email)])
        query)
      (if-let [org-id (:org_id sign-in-token)]
        (sql/where query [:= :users.org_id org-id])
        query)
      (if-let [login (:login sign-in-token)]
        (sql/where query [:= :users.login login])
        query)
      (sql/select query :users.*)
      (sql-format query))))

(defn user-for-sign-in-token [sign-in-token authentication-system-id tx]
  (let [query (user-for-sign-in-token-query sign-in-token authentication-system-id)
        resultset (jdbc-query tx query)]
    (when (> (count resultset) 1)
      (throw (ex-info
              "More than one user matched the sign-in request."
              {:status 400})))
    (or (first resultset)
        (throw (ex-info
                "No valid user account could be identified for this sign-in request."
                {:status 400})))))

(defn authentication-sign-in
  [{{authentication-system-id :authentication-system-id} :route-params
    {token :token} :query-params-raw
    tx :tx request-method :request-method
    :as request}]
  (let [authentication-system (authentication-system! authentication-system-id tx)
        sign-in-token (unsign-external-token token authentication-system)
        sign-in-request-token (unsign-internal-token
                               (:sign_in_request_token sign-in-token)
                               authentication-system)]
    (debug 'sign-in-token sign-in-token)
    (if-not (:success sign-in-token)
      {:status 400
       :headers (case request-method
                  :get  {"Content-Type" "text/plain"}
                  :post {})
       :body (:error_message sign-in-token)}
      (if-let [user (user-for-sign-in-token sign-in-token authentication-system-id tx)]
        (let [user-session (session/create-user-session
                            user authentication-system-id request
                            :user-session (select-keys sign-in-token [:external_session_id]))]
          {:body user
           :status (case request-method
                     :post 200
                     :get 302)
           :headers  (case request-method
                       :post {}
                       :get {"Location" (or (:return_to sign-in-request-token) (redirect-target tx user))})
           :cookies {leihs.core.constants/USER_SESSION_COOKIE_NAME
                     {:value (:token user-session)
                      :http-only true
                      :max-age (* 10 356 24 60 60)
                      :path "/"
                      :secure (:sessions_force_secure (:settings request))}}})
        {:status 404}))))

(defn routes [request]
  (case (:request-method request)
    :post (authentication-request request)
    (authentication-sign-in request)))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
