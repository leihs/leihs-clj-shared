(ns leihs.core.paths
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.url.query-params :as query-params]
    [leihs.core.core :refer [keyword str presence]]

    [bidi.verbose :refer [branch param leaf]]
    [bidi.bidi :refer [path-for match-route]]))

(def core-user-paths
  (branch "/user/"
          (param [#"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})|(me)" :user-id])
          (leaf "" :me-user)
          (leaf "/auth-info" :auth-info)))

(def core-paths
  (branch ""
          (leaf "/" :home)
          (leaf "/admin/" :admin)
          (leaf "/procure" :procurement)
          (leaf "/borrow" :borrow)
          (branch "/manage" 
                  (leaf "/" :lending)
                  (branch "/" (param :inventory_pool_id)
                          (leaf "/daily" :daily)))

          (branch "/sign-in"
                  (leaf "" :sign-in)
                  (leaf "/password-authentication" :password-authentication)
                  (leaf "/email-authentication" :email-authentication)
                  (branch "/external-authentication/"
                          (param :authentication-system-id)
                          (leaf "" :external-authentication)
                          (leaf "/request" :external-authentication-request)
                          (leaf "/sign-in" :external-authentication-sign-in)))
          (leaf "/sign-out" :sign-out)
          core-user-paths))


(def paths* (atom core-paths))

(defn path
  ([kw]
   (path kw {}))
  ([kw route-params]
   (apply (partial path-for @paths* kw)
          (->> route-params
               (merge {:user-id "me"})
               (into []) flatten)))
  ([kw route-params query-params]
   (str (path kw route-params) "?"
        (query-params/encode query-params))))

