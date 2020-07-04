(ns leihs.core.sign-in.external-authentication.back
  (:refer-clojure :exclude [str keyword cond])
  (:require
    [cemerick.url :refer [url-encode]]
    [better-cond.core :refer [cond]]
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [leihs.core.auth.session :as session]
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.paths :refer [path]]
    [leihs.core.redirects :refer [redirect-target]]
    [leihs.core.sign-in.shared :refer [auth-system-base-query-for-unique-id user-query-for-unique-id]]
    [leihs.core.sql :as sql]

    [buddy.core.keys :as keys]
    [buddy.sign.jwt :as jwt]
    [clj-time.core :as time]
    [compojure.core :as cpj]
    [ring.util.response :refer [redirect]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]))

(def skip-authorization-handler-keys
  "These keys needs the be added to the list of the skipped handler keys
  in each subapp in order to make the complete login process work in them."
  #{:external-authentication-request
    :external-authentication-sign-in})

(defn auth-system-user-query [user-unique-id authentication-system-id]
  (-> (auth-system-base-query-for-unique-id user-unique-id authentication-system-id)
      (sql/merge-select
        [(sql/call :row_to_json :authentication_systems) :authentication_system]
        [(sql/call :row_to_json :users) :user])
      sql/format))

(defn authentication-system-user-data
  [user-unique-id authentication-system-id tx]
  (when-let [authentication-system-and-user
             (->> (auth-system-user-query
                    user-unique-id authentication-system-id)
                  (jdbc/query tx) first)]
    (merge authentication-system-and-user
           (->> (-> (sql/select :*)
                    (sql/from :authentication_systems_users)
                    (sql/merge-where [:= :authentication_systems_users.authentication_system_id
                                      (-> authentication-system-and-user :authentication_system :id)])
                    (sql/merge-where [:= :authentication_systems_users.user_id
                                      (-> authentication-system-and-user :user_id:id)])
                    sql/format)
                (jdbc/query tx) first))))

(defn authentication-system-user-data!
  [user-unique-id authentication-system-id tx]
  (or (authentication-system-user-data
        user-unique-id authentication-system-id tx)
      (throw (ex-info
               (str "External authentication system for existing user "
                    user-unique-id " not found or not enabled")
               {:status 500}))))

(defn prepare-key-str [s]
  (->> (-> s (clojure.string/split #"\n"))
       (map clojure.string/trim)
       (map presence)
       (filter identity)
       (clojure.string/join "\n")))

(defn private-key! [s]
  (-> s prepare-key-str keys/str->private-key
      (or (throw
            (ex-info "Private key error!"
                     {:status 500})))))

(defn public-key! [s]
  (-> s prepare-key-str keys/str->public-key
      (or (throw
            (ex-info "Public key error!"
                     {:status 500})))))

(defn claims! [user authentication-system settings return-to]
  {:email (when (:send_email authentication-system) (:email user))
   :login (when (:send_login authentication-system) (:login user))
   :org_id (when (:send_org_id authentication-system) (:org_id user))
   :exp (time/plus (time/now) (time/seconds 90))
   :iat (time/now)
   :server_base_url (:external_base_url settings)
   :return_to (presence return-to)
   :path (path :external-authentication-sign-in
               {:authentication-system-id (:id authentication-system)})})


(defn authentication-system [tx authentication-system-id]
  (->> (-> (sql/select :*)
           (sql/from :authentication_systems)
           (sql/merge-where [:= :id authentication-system-id])
           (sql/format))
       (jdbc/query tx) first))

(defn user [tx user-unique-id]
  (->> (-> user-unique-id
           user-query-for-unique-id
           sql/format)
       (jdbc/query tx) first))

(defn ext-auth-system-token-url
  ([tx user-unique-id authentication-system-id settings]
   (ext-auth-system-token-url tx user-unique-id authentication-system-id settings nil))
  ([tx user-unique-id authentication-system-id settings return-to]
   (cond
     :let [authentication-system (authentication-system tx authentication-system-id)
           user (or (user tx user-unique-id)
                    {:email user-unique-id})
           priv-key (-> authentication-system :internal_private_key private-key!)
           claims (claims! user authentication-system settings return-to)
           token (jwt/sign claims priv-key {:alg :es256})]
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
               (sql/merge-where [:= :authentication_systems.id id])
               sql/format)
           (jdbc/query tx) first)
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
        (sql/merge-where query [:= (sql/raw "lower(users.email)") (str/lower-case email)])
        query)
      (if-let [org-id (:org_id sign-in-token)]
        (sql/merge-where query [:= :users.org_id org-id])
        query)
      (if-let [login (:login sign-in-token)]
        (sql/merge-where query [:= :users.login login])
        query)
      (sql/merge-select query :users.*)
      (sql/format query))))

(defn user-for-sign-in-token [sign-in-token authentication-system-id tx]
  (let [query (user-for-sign-in-token-query sign-in-token authentication-system-id)
        resultset (jdbc/query tx query)]
    (when (> (count resultset) 1)
      (throw (ex-info
               "More than one user matched the sign-in request."
               {:status 400})))
    (or (first resultset)
        (throw (ex-info
                 "No valid user account could be identified for this sign-in request."
                 {:status 400})))))

(defn authentication-sign-in-get
  [{{authentication-system-id :authentication-system-id} :route-params
    {token :token} :query-params-raw
    tx :tx
    :as request}]
  (let [authentication-system (authentication-system! authentication-system-id tx)
        external-pub-key (-> authentication-system :external_public_key public-key!)
        sign-in-token (jwt/unsign token external-pub-key {:alg :es256})
        internal-pub-key (-> authentication-system :internal_public_key public-key!)
        sign-in-request-token (jwt/unsign (:sign_in_request_token sign-in-token)
                                          internal-pub-key {:alg :es256})]

    (logging/debug 'sign-in-token sign-in-token)
    (logging/debug 'sign-in-request-token sign-in-request-token)
    (if-not (:success sign-in-token)
      {:status 400
       :headers {"Content-Type" "text/plain"}
       :body (:error_message sign-in-token)}
      (if-let [user (user-for-sign-in-token sign-in-token authentication-system-id tx)]
        (let [user-session (session/create-user-session user authentication-system-id request)]
          {:status 302
           :headers {"Location" (or (:return_to sign-in-request-token) (redirect-target tx user))}
           :cookies {leihs.core.constants/USER_SESSION_COOKIE_NAME
                     {:value (:token user-session)
                      :http-only true
                      :max-age (* 10 356 24 60 60)
                      :path "/"
                      :secure (:sessions_force_secure (:settings request))}}})
        {:status 404}))))

(defn authentication-sign-in-post
  [{{authentication-system-id :authentication-system-id} :route-params
    {token :token} :query-params-raw
    tx :tx
    :as request}]
  (let [authentication-system (authentication-system! authentication-system-id tx)
        external-pub-key (-> authentication-system :external_public_key public-key!)
        sign-in-token (jwt/unsign token external-pub-key {:alg :es256})
        internal-pub-key (-> authentication-system :internal_public_key public-key!)
        sign-in-request-token (jwt/unsign (:sign_in_request_token sign-in-token)
                                          internal-pub-key {:alg :es256})]
    (logging/debug 'sign-in-token sign-in-token)
    (if-not (:success sign-in-token)
      {:status 400
       :body (:error_message sign-in-token)}
      (if-let [user (user-for-sign-in-token sign-in-token authentication-system-id tx)]
        (let [user-session (session/create-user-session user authentication-system-id request)]
          {:body user
           :status 200
           :cookies {leihs.core.constants/USER_SESSION_COOKIE_NAME
                     {:value (:token user-session)
                      :http-only true
                      :max-age (* 10 356 24 60 60)
                      :path "/"
                      :secure (:sessions_force_secure (:settings request))}}})
        {:status 404}))))

(def routes
  (cpj/routes
    (cpj/POST (path :external-authentication-request
                    {:authentication-system-id ":authentication-system-id"})
              [] #'authentication-request)
    (cpj/POST (path :external-authentication-sign-in
                    {:authentication-system-id ":authentication-system-id"})
              [] #'authentication-sign-in-post)
    (cpj/GET (path :external-authentication-sign-in
                   {:authentication-system-id ":authentication-system-id"})
             [] #'authentication-sign-in-get)))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
