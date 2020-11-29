(ns leihs.core.routes
  (:refer-clojure :exclude [str keyword])
  (:require [leihs.core.core :refer [keyword str presence]])
  (:require
    [leihs.core.sign-in.external-authentication.back :as external-authentication]
    [leihs.core.sign-in.back :as sign-in]
    [leihs.core.sign-out.back :as sign-out]
    ))


(def skip-authorization-handler-keys
  #{:external-authentication-request
    :external-authentication-sign-in
    :sign-in
    :sign-out})

(def no-spa-handler-keys
  #{:external-authentication-sign-in
    :sign-in})

(def all-granted (constantly true))

(def resolve-table
  {:external-authentication-request  {:handler external-authentication/routes
                                      :authorizers [all-granted]}

   :external-authentication-sign-in {:handler external-authentication/routes
                                     :authorizers [all-granted]}
   :sign-in {:handler sign-in/routes
             :authorizers [all-granted]}
   :sign-out {:handler sign-out/routes
              :authorizers [all-granted]}})
