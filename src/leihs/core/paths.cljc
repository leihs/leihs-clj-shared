(ns leihs.core.paths
  (:refer-clojure :exclude [str keyword])
  (:require
   [bidi.bidi :refer [path-for match-route]]
   [bidi.verbose :refer [branch param leaf]]

   [leihs.core.core :refer [keyword str presence]]
   [leihs.core.url.query-params :as query-params]))

(def core-paths
  (branch ""
          ;; root
          (leaf "/" :home)

          ;; subapps
          (leaf "/admin/" :admin)
          (leaf "/procure" :procurement)
          (leaf "/borrow" :borrow)
          (branch "/manage"
                  (leaf "" :manage)
                  (branch "/" (param :inventory_pool_id)
                          (leaf "/daily" :daily)))

          ;; concerns of subapp `my` which are consumed by other subapps also (at least in parts)
          (branch "/my"
                  (leaf "/auth-info" :auth-info)
                  (leaf "/language" :language))
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
          (leaf "/reset-password" :reset-password)))

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
   (path kw {}))
  ([kw route-params]
   (apply (partial path-for @paths* kw)
          (->> route-params
               (merge {:user-id "me"})
               encode-route-params
               (into []) flatten)))
  ([kw route-params query-params]
   (let [p (path kw route-params)
         q (query-params/encode query-params)]
     (if (presence q)
       (str p "?" q)
       p)))
  ([kw route-params query-params fragment]
   (let [p (path kw route-params query-params)]
     (if (presence fragment)
       (str p "#" fragment)
       p))))

;(path :reset-password {})
