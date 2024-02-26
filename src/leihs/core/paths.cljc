(ns leihs.core.paths
  (:refer-clojure :exclude [str keyword])
  (:require
   [bidi.bidi :refer [path-for match-route]]
   [bidi.verbose :refer [branch param leaf]]

   [leihs.core.core :refer [keyword str presence]]
   [leihs.core.url.query-params :as query-params]

   [logbug.debug :as debug]
   [taoensso.timbre :refer [info warn error spy]]))

(def core-user-paths
  (branch "/my/user/"
          (param [#"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})|(me)" :user-id])
          (leaf "" :my-user)
          (leaf "/auth-info" :auth-info)))

(def core-paths
  (branch ""
          (leaf "/" :home)
          (leaf "/reset-password" :reset-password)
          (leaf "/admin/" :admin)
          (leaf "/procure" :procurement)
          (branch "/borrow"
                  (leaf "" :borrow)
                  (leaf "/user/documents" :user-documents))
          (branch "/manage"
                  (leaf "" :manage)
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
          (branch "/sign-out"
                  (leaf "" :sign-out)
                  (branch "/external-authentication/"
                          (param :authentication-system-id)
                          (leaf "/sso-sign-out" :external-authentication-sso-sign-out)))
          core-user-paths))

(comment (path :external-authentication-sso-sign-out {:authentication-system-id "foo"}))

(def paths* (atom core-paths))

;(bidi.bidi/route-seq @paths*)

(defn encode-route-param [param]
  "Encode route-param but keep the first : because we use this
  in pathmatchers frequently."
  (let [first-letter (apply str (take 1 (str param)))
        rest-letters (apply str (drop 1 (str param)))]
    (str "" (if (= first-letter ":")
              first-letter
              (query-params/encode-primitive first-letter))
         (query-params/encode-primitive rest-letters))))

(defn encode-route-params [m]
  (zipmap (keys m)
          (map encode-route-param (vals m))))

(defn path
  ([kw]
   (println ">o> path-1")
   (path kw {}))

  ([kw route-params]
   (println ">o> path-2")
   (println ">o> path-2 kw=" kw)
   (println ">o> path-2 route-params=" route-params)
   (spy (apply (spy (partial path-for @paths* kw))
               (->> route-params
                    (merge {:user-id "me"})
                    spy
                    encode-route-params
                    spy
                    (into []) flatten
                    spy))))

  ([kw route-params query-params]
   (println ">o> path-3")
   (let [p (path kw route-params)
         q (query-params/encode query-params)]
     (if (presence q)
       (str p "?" q)
       p)))

  ([kw route-params query-params fragment]
   (println ">o> path-4")
   (let [p (path kw route-params query-params)]
     (if (presence fragment)
       (str p "#" fragment)
       p))))

;(path :reset-password {})
