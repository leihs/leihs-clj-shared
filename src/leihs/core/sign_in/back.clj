(ns leihs.core.sign-in.back
  (:refer-clojure :exclude [keyword])
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.string]
   [compojure.core :as cpj]
   [leihs.core.anti-csrf.back :refer [anti-csrf-props]]
   [leihs.core.auth.session :as session]
   [leihs.core.core :refer [presence presence! keyword]]
   [leihs.core.locale :as locale]
   [leihs.core.paths :refer [path]]
   [leihs.core.redirects :refer [redirect-target]]
   [leihs.core.remote-navbar.shared :refer [navbar-props]]
   [leihs.core.sign-in-sign-out.shared :refer [auth-system-user-base-query merge-identify-user]]
   [leihs.core.sign-in.external-authentication.back :refer [ext-auth-system-token-url]]
   [leihs.core.sign-in.password-authentication.core :refer [password-checked-user]]
   [leihs.core.sign-in.simple-login :as simple-login]
   [leihs.core.sql :as sql]
   [leihs.core.ssr :as ssr]
   [leihs.core.ssr-engine :as js-engine]
   [logbug.debug :as debug]
   [ring.util.response :refer [redirect]]
   [taoensso.timbre :refer [debug error info spy warn]]))

(defn auth-system-query [user-id]
  (->
   auth-system-user-base-query
   (sql/merge-where [:= :users.id user-id])
   (sql/merge-select :authentication_systems.id
                     :authentication_systems.type
                     :authentication_systems.name
                     :authentication_systems.description
                     :authentication_systems.external_sign_in_url)
   sql/format))

(defn auth-systems-for-user [tx {user-id :id}]
  (if-not user-id
    []
    (->> user-id
         auth-system-query
         (jdbc/query tx))))

(defn pwd-auth-system [tx]
  (-> (sql/select :*)
      (sql/from :authentication_systems)
      (sql/where [:= :type "password"])
      sql/format
      (->> (jdbc/query tx))
      first))

(defn user-with-unique-id [tx user-unique-id]
  (-> (sql/select :*)
      (sql/from :users)
      (merge-identify-user user-unique-id)
      sql/format
      (->> (jdbc/query tx))
      first))

(def sign-in-page-renderer* (atom nil))
(defn use-sign-in-page-renderer [renderer]
  (reset! sign-in-page-renderer* renderer))

(defn render-sign-in-page
  ([user-param user request] (render-sign-in-page user-param user request {}))
  ([user-param
    user
    {:keys [tx pwd-auth-system-enabled] :as request}
    {auth-systems :authSystems :as extra-props}]
   (let [user-password (some #(-> % :type (= "password")) auth-systems)
         sign-in-page-params
         (merge-with into
                     {:navbar (navbar-props request),
                      :authFlow {:user user-param,
                                 :showPasswordSection (-> user-password nil? not)
                                 :passwordButtonText (if user-password
                                                       "password_forgot_button_text"
                                                       "password_create_button_text")
                                 :passwordLink "/forgot-password"}}
                     (anti-csrf-props request)
                     extra-props)]
     (debug 'sign-in-page-params sign-in-page-params)
     (if (some? @sign-in-page-renderer*)
       (@sign-in-page-renderer* sign-in-page-params)
       (simple-login/sign-in-view sign-in-page-params)))))

(defn render-sign-in-page-for-invalid-user [user-param user request]
  (render-sign-in-page
   user-param
   user
   request
   {:flashMessages [{:messageID "sign_in_invalid_user_flash_message"
                     :level "error"}]}))

(defn sign-up-auth-systems [tx user-email]
  (->> (-> (sql/select :*)
           (sql/from :authentication_systems)
           (sql/merge-where [:<> :authentication_systems.sign_up_email_match nil])
           (sql/merge-where [(keyword "~*") user-email :authentication_systems.sign_up_email_match])
           (sql/format))
       (jdbc/query tx)))

(defn render-sign-in [user-unique-id
                      user
                      auth-systems
                      {tx :tx settings :settings {return-to :return-to} :params :as request}]
  (let [render-sign-in-page-fn #(render-sign-in-page
                                 user-unique-id
                                 user
                                 request
                                 {:authSystems auth-systems
                                  :authFlow {:returnTo return-to}})]
    (if (and (= (count auth-systems) 1)
             (not (:password_sign_in_enabled user)))
      (let [auth-system (first auth-systems)]
        (if (= (:type auth-system) "external")

          (render-sign-in-page-fn)))
      (render-sign-in-page-fn))))

(defn sign-in-redirect
  [auth-system user-unique-id {tx :tx settings :settings :as request}]
  (redirect
   (ext-auth-system-token-url
    tx
    user-unique-id
    (:id auth-system)
    settings)))

(defn handle-first-step
  "Try to find a user account from the user param, then find all the availabe auth systems.
   1. If there is no user given, render initial page again.
   2. - If user does not exist or
      - user's account is disabled or
      - has no auth systems and his password sign in is disabled or
      - has no auth systems, his password sign in is enabled, but he doesn't have email nor password
      => show error
   3. If there is only an external auth system and password sign is is disabled, redirect to it.
   4. Otherwise show a form with all auth systems."
  [{tx :tx, {user-param :user return-to :return-to} :params :as request}]
  (let [user-unique-id (presence user-param)
        user (user-with-unique-id tx user-unique-id)
        user-auth-systems (auth-systems-for-user tx user)
        sign-up-auth-systems (->> (sign-up-auth-systems tx user-unique-id)
                                  (filter (fn [sign-up-auth-system]
                                            (if (some #(= (:id sign-up-auth-system) %) (map :id user-auth-systems))
                                              false
                                              true))))
        all-available-auth-systems (concat user-auth-systems sign-up-auth-systems)]

    (debug 'user user 'user-auth-systems user-auth-systems 'sign-up-auth-systems sign-up-auth-systems)

    (cond

        ; there is not even an login/e-mail/org-id
      (nil? user-unique-id) (render-sign-in-page user-unique-id
                                                 user
                                                 request
                                                 {:authFlow {:returnTo return-to}})

        ; no user found and no sign-up-auth-systems
      (and (not user)
           (empty? sign-up-auth-systems)) (render-sign-in-page-for-invalid-user
                                           user-unique-id
                                           user
                                           request)

        ; no user but at least one matching sing up system
      (and (not user)
           (not-empty sign-up-auth-systems)) (render-sign-in-page
                                              user-unique-id
                                              user
                                              request
                                              {:authSystems sign-up-auth-systems})

        ; we have a matching user for all the remaining cases

        ; the user is not enabled
      (-> user :account_enabled not) (render-sign-in-page-for-invalid-user
                                      user-unique-id
                                      user
                                      request)

        ; the user is enabled but there no available sign-in or sign-up systems and
        ; his password sign in is not enabled or it is enabled but he doesn't have an email
      (and (empty? user-auth-systems)
           (empty? sign-up-auth-systems)
           (or (-> user :password_sign_in_enabled not)
               (and (-> user :password_sign_in_enabled)
                    (-> user :email not))))

      (render-sign-in-page-for-invalid-user user-unique-id user request)

        ; the single available auth system is external and the user can not reset the password
      (and (= 1 (count all-available-auth-systems))
           (= (-> all-available-auth-systems first :type) "external")
           (not (:password_sign_in_enabled user))) (sign-in-redirect
                                                    (first all-available-auth-systems)
                                                    user-unique-id
                                                    request)

        ; else continue with sign-in / sign-up
      :else (render-sign-in user-unique-id
                            user
                            all-available-auth-systems
                            (assoc request
                                   :pwd-auth-system-enabled
                                   (:enabled (pwd-auth-system tx)))))))

(defn handle-second-step
  "validate given user and password params.
  on success, set cookie and redirect, otherwise render page again with error.
  param `invisible-pw` signals that password has been autofilled,
  in which case an error is ignored and it is handled like first step"
  [{tx :tx, {user-param :user,
             password :password,
             invisible-pw :invisible-password,
             return-to :return-to,
             :as form-params} :form-params-raw,
    settings :settings,
    :as request}]
  (if-let [user (password-checked-user user-param password)]
    (let [user-session
          (session/create-user-session
           user
           leihs.core.constants/PASSWORD_AUTHENTICATION_SYSTEM_ID
           request)
          location (or (presence return-to) (redirect-target tx user))
          cookies {:cookies {leihs.core.constants/USER_SESSION_COOKIE_NAME
                             {:value (:token user-session),
                              :http-only true,
                              :max-age (* 10 356 24 60 60),
                              :path "/",
                              :secure (:sessions_force_secure settings)}}}
          response (if (= (-> request :accept :mime) :json)
                     (merge {:status 200, :body {:location location}} cookies)
                     (merge {:status 302, :headers {"Location" location}} cookies))]
      (locale/setup-language-after-sign-in request response user))
    (if (not (nil? invisible-pw))
      (handle-first-step request)
      {:status 401,
       :headers {"Content-Type" "text/html"},
       :body
       (render-sign-in-page
        user-param
        nil
        request
        {:flashMessages [{:messageID "sign_in_wrong_password_flash_message"
                          :level "error"}]})})))

(defn sign-in-get
  [{tx :tx, {return-to :return-to} :params :as request}]
  (if-let [user (:authenticated-entity request)]
    ; shortcut: if already signed in, skip everything but redirect like succcess
    (redirect (or (presence return-to) (redirect-target tx user)))
    (handle-first-step request)))

(defn sign-in-post
  [{tx :tx,
    {user-param :user, password :password return-to :return-to} :form-params,
    :as request}]
  ; shortcut: if already signed in, skip everything but redirect like succcess
  (if-let [user (:authenticated-entity request)]
    (redirect (or (presence return-to) (redirect-target tx user)))
    ; if no user or password was entered handle like step 1
    (if (or (nil? user-param) (nil? password))
      (handle-first-step request)
      (handle-second-step request))))

(def routes
  (cpj/routes
   (cpj/GET (path :sign-in) [] #'sign-in-get)
   (cpj/POST (path :sign-in) [] #'sign-in-post)))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
