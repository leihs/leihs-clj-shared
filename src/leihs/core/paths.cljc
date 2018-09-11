(ns leihs.core.paths
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.url.query-params :as query-params]
    [leihs.core.core :refer [keyword str presence]]

    [bidi.verbose :refer [branch param leaf]]
    [bidi.bidi :refer [path-for match-route]]))


(def core-paths
  (branch ""
          (leaf "/" :home)
          (leaf "/admin" :admin)
          (leaf "/procure" :procurement)
          (leaf "/manage" :lending)
          (leaf "/borrow" :borrow)

          (branch "/sign-in"
                  (leaf "" :sign-in)
                  (leaf "/password-authentication" :password-authentication)
                  (leaf "/email-authentication" :email-authentication)
                  (branch "/external-authentication/"
                          (param :authentication-system-id)
                          (leaf "" :external-authentication)))
          (leaf "/sign-out" :sign-out)))


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

