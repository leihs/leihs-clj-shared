(ns leihs.core.sign-in.back
  (:refer-clojure :exclude [str keyword])
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]
    [compojure.core :as cpj]
    [leihs.core.auth.session :as session]
    [leihs.core.core :refer [presence presence!]]
    [leihs.core.locale :as locale]
    [leihs.core.sign-in.password-authentication.back :refer [password-check-query]]
    [leihs.core.paths :refer [path]]
    [leihs.core.redirects :refer [redirect-target]]
    [leihs.core.remote-navbar.shared :refer [navbar-props]]
    [leihs.core.sql :as sql]
    [leihs.core.ssr :as ssr]
    [leihs.core.ssr-engine :as js-engine]
    [leihs.core.sign-in.external-authentication.back :refer [ext-auth-system-token-url]]
    [leihs.core.sign-in.shared :refer [auth-system-base-query-for-unique-id merge-identify-user]]
    [ring.util.response :refer [redirect]]
    
    [clojure.tools.logging :as log]
    [logbug.debug :as debug]

    ))

(defn auth-system-query
  [unique-id]
  (-> unique-id
      auth-system-base-query-for-unique-id
      (sql/merge-select :authentication_systems.id
                        :authentication_systems.type
                        :authentication_systems.name
                        :authentication_systems.description
                        :authentication_systems.external_sign_in_url)
      sql/format))

(defn auth-systems
  [tx unique-id]
  (->>
    unique-id
    presence!
    auth-system-query
    (jdbc/query tx)))

(defn user-with-unique-id [tx user-param]
  (-> (sql/select :*)
      (sql/from :users)
      (merge-identify-user user-param)
      sql/format
      (->> (jdbc/query tx))
      first))

(defn render-sign-in-page
  ([user-param request] (render-sign-in-page user-param request {}))
  ([user-param request extra-props]
   (let [tx (:tx request)
         auth-entity (:authenticated-entity request)]
     (ssr/render-page-base
       (js-engine/render-react
         "SignInPage"
         (merge-with into
                     {:navbar (navbar-props request),
                      :authFlow
                      {:user user-param, :forgotPasswordLink "/forgot-password"}}
                     extra-props))))))

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
  [{tx :tx, {user-param :user return-to :return-to} :params, settings :settings, :as request}]
  (if (nil? user-param)
    (render-sign-in-page user-param request
                         {:authFlow {:returnTo return-to}})
    (let [user-auth-systems (auth-systems tx user-param)
          user (user-with-unique-id tx user-param)]
      (if (or (not (:account_enabled user))
              (and (empty? user-auth-systems)
                   (not (:password_sign_in_enabled user))))
        (render-sign-in-page
          user-param
          request
          {:flashMessages [error-flash-invalid-user]})
        (let [render-sign-in-page-fn #(render-sign-in-page
                                        user-param
                                        request
                                        {:authSystems user-auth-systems
                                         :authFlow {:returnTo return-to}})]
          (if (and (= (count user-auth-systems) 1)
                   (not (:password_sign_in_enabled user)))
            (let [auth-system (first user-auth-systems)]
              (if (= (:type auth-system) "external")
                (redirect
                  (ext-auth-system-token-url
                    tx
                    user-param
                    (:id auth-system)
                    settings))
                (render-sign-in-page-fn)))
            (render-sign-in-page-fn)))))))

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
  [{tx :tx, settings :settings, {user-param :user} :query-params, :as request}]
  (if-let [user (:authenticated-entity request)]
    ; shortcut: if already signed in, skip everything but redirect like succcess
    (redirect (redirect-target tx user))
    (handle-first-step request)))

(defn sign-in-post
  [{tx :tx,
    {user-param :user, password :password} :form-params,
    settings :settings,
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
