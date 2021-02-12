(ns leihs.core.sign-in.back
  (:refer-clojure :exclude [str keyword])

  (:require
    [leihs.core.ds :as ds]
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [compojure.core :as cpj]
    [leihs.core.auth.session :as session]
    [leihs.core.core :refer [presence presence! keyword]]
    [leihs.core.locale :as locale]
    [leihs.core.sign-in.password-authentication.back :refer [password-check-query]]
    [leihs.core.paths :refer [path]]
    [leihs.core.redirects :refer [redirect-target]]
    [leihs.core.remote-navbar.shared :refer [navbar-props]]
    [leihs.core.sql :as sql]
    [leihs.core.ssr :as ssr]
    [leihs.core.ssr-engine :as js-engine]
    [leihs.core.anti-csrf.back :refer [anti-csrf-props]]
    [leihs.core.sign-in.external-authentication.back :refer [ext-auth-system-token-url]]
    [leihs.core.sign-in.shared :refer [auth-system-user-base-query merge-identify-user]]
    [ring.util.response :refer [redirect]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as log]
    [logbug.debug :as debug]

    ))

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

(defn user-with-unique-id [tx user-unique-id]
  (-> (sql/select :*)
      (sql/from :users)
      (merge-identify-user user-unique-id)
      sql/format
      (->> (jdbc/query tx))
      first))

(defn render-sign-in-page
  ([user-param request] (render-sign-in-page user-param request {}))
  ([user-param {tx :tx :as request} extra-props]
   (let [sign-in-page-params  (merge-with into
                                          {:navbar (navbar-props request),
                                           :authFlow
                                           {:user user-param, :forgotPasswordLink "/forgot-password"}}
                                          (anti-csrf-props request)
                                          extra-props)]
     (log/debug 'sign-in-page-params sign-in-page-params)
     (ssr/render-page-base
       (js-engine/render-react "SignInPage" sign-in-page-params)))))

(def error-flash-invalid-user
  {:level "error",
   :message
     (clojure.string/join
       " \n"
       ["Anmelden ist mit diesem Benutzerkonto nicht möglich! "
        "Bitte prüfen Sie Ihre E-Mail-Adresse oder den Benutzernamen. Kontaktieren Sie den leihs-Support, falls das Problem weiterhin besteht."])})

(def error-flash-invalid-password
  {:level "error",
   :message
     (clojure.string/join
       " \n"
       ["Falsches Passwort! "
        "Überprüfen Sie Ihr Passwort und versuchen Sie es erneut. Kontaktieren Sie den leihs-Support, falls das Problem weiterhin besteht."])})

(defn render-sign-in-page-for-invalid-user [user-param request]
  (render-sign-in-page
    user-param
    request
    {:flashMessages [error-flash-invalid-user]}))

(defn sign-up-auth-systems [tx user-email]
  (->> (-> (sql/select :*)
           (sql/from :authentication_systems)
           (sql/merge-where [:<> :authentication_systems.sign_up_email_match nil])
           (sql/merge-where [(keyword "~*") user-email :authentication_systems.sign_up_email_match])
           (sql/format))
       (jdbc/query tx)))


(defn render-sign-in [user-unique-id user auth-systems
                      {tx :tx settings :settings
                       {return-to :return-to} :params :as request}]
  (let [render-sign-in-page-fn #(render-sign-in-page
                                  user-unique-id
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
  "try to find a user account from the user param,
  then find all the availabe auth systems.
  if there is no user given, render initial page again.
  if user does not exist or
    user's account is disabled or
    has no auth systems and his password sign in is disabled
    => show error
  if there is only an external auth system and
    password sign is is disabled, redirect to it.
  otherwise show a form with all auth systems."
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

    (log/debug 'user user 'user-auth-systems user-auth-systems 'sign-up-auth-systems sign-up-auth-systems)

      (cond

        ; there is not even an login/e-mail/org-id
        (nil? user-unique-id) (render-sign-in-page user-unique-id request
                                                   {:authFlow {:returnTo return-to}})

        ; no user found and no sign-up-auth-systems
        (and (not user)
             (empty? sign-up-auth-systems)) (render-sign-in-page-for-invalid-user
                                              user-unique-id request)

        ; no user but at least one matching sing up system
        (and (not user)
             (not-empty sign-up-auth-systems)) (render-sign-in-page
                                                 user-unique-id request
                                                 {:authSystems sign-up-auth-systems})

        ; we have a matching user for all the remaining cases

        ; the user is not enabled
        (-> user :account_enabled not) (render-sign-in-page-for-invalid-user
                                        user-unique-id request)

        ; the user is enabled but there no available sign in systems, but
        ; he can reset his password to sing in via the new password
        (and (empty? user-auth-systems)
             (empty? sign-up-auth-systems)
             (-> user :password_sign_in_enabled not)) (render-sign-in-page-for-invalid-user
                                                        user-unique-id request)

        ; the single available auth system is external and the user can not reset the password
        (and (= 1 (count all-available-auth-systems))
             (= (-> all-available-auth-systems first :type) "external")
             (not (:password_sign_in_enabled user))) (sign-in-redirect
                                                       (first all-available-auth-systems)
                                                       user-unique-id
                                                       request)

        ; else continue with sign-in / sign-up
        :else (render-sign-in user-unique-id user
                              all-available-auth-systems
                              request))))

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
  (if-let [user (->> [user-param password]
                     (apply password-check-query)
                     (jdbc/query tx)
                     first)]
    (let [user-session
          (session/create-user-session
            user
            leihs.core.constants/PASSWORD_AUTHENTICATION_SYSTEM_ID request)
          response {:status 302,
                    :headers {"Location" (or (presence return-to)
                                             (redirect-target tx user))},
                    :cookies
                    {leihs.core.constants/USER_SESSION_COOKIE_NAME
                     {:value (:token user-session),
                      :http-only true,
                      :max-age (* 10 356 24 60 60),
                      :path "/",
                      :secure (:sessions_force_secure settings)}}}]
      (locale/setup-language-after-sign-in request response user))
    (if (not (nil? invisible-pw))
      (handle-first-step request)
      {:status 401,
       :headers {"Content-Type" "text/html"},
       :body
       (render-sign-in-page
         user-param
         request
         {:flashMessages [error-flash-invalid-password]})})))

(defn sign-in-get
  [{tx :tx :as request}]
  (if-let [user (:authenticated-entity request)]
    ; shortcut: if already signed in, skip everything but redirect like succcess
    (redirect (redirect-target tx user))
    (handle-first-step request)))

(defn sign-in-post
  [{tx :tx,
    {user-param :user, password :password} :form-params,
    :as request}]
  ; shortcut: if already signed in, skip everything but redirect like succcess
  (if-let [user (:authenticated-entity request)]
    (redirect (redirect-target tx user))
    ; if no user or password was entered handle like step 1
    (if (or (nil? user-param) (nil? password))
      (handle-first-step request)
      (handle-second-step request))))

(def routes
  (cpj/routes
    (cpj/GET (path :sign-in) [] #'sign-in-get)
    (cpj/POST (path :sign-in) [] #'sign-in-post)))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
