(ns leihs.core.routes
  (:require
   [leihs.core.language.back :as language]
   [leihs.core.sign-in.back :as sign-in]
   [leihs.core.sign-in.external-authentication.back :as external-authentication]
   [leihs.core.sign-out.back :as sign-out]
   [leihs.core.sign-out.sso :as sso-sign-out]))

(def skip-authorization-handler-keys
  #{:external-authentication-request
    :external-authentication-sign-in
    :external-authentication-sso-sign-out
    :sign-in
    :sign-out
    :language})

(def no-spa-handler-keys
  #{:external-authentication-sign-in
    :external-authentication-sso-sign-out
    :sign-in
    :language})

(def all-granted (constantly true))

(def resolve-table
  {:language {:handler #'language/routes
              :authorizers [all-granted]}
   :external-authentication-request  {:handler external-authentication/routes
                                      :authorizers [all-granted]}

   :external-authentication-sign-in {:handler external-authentication/routes
                                     :authorizers [all-granted]}
   :sign-in {:handler sign-in/routes
             :authorizers [all-granted]}
   :sign-out {:handler sign-out/routes
              :authorizers [all-granted]}
   :external-authentication-sso-sign-out {:handler sso-sign-out/routes
                                          :authorizers [all-granted]}})



