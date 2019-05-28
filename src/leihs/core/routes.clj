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

(def resolve-table
  {:external-authentication-request external-authentication/routes
   :external-authentication-sign-in external-authentication/routes
   :sign-in sign-in/routes
   :sign-out sign-out/routes
   })
