(ns leihs.core.sign-in-sign-out.external-authentication
  (:refer-clojure :exclude [str keyword cond])
  (:require
   [buddy.core.keys :as keys]
   [buddy.sign.jwt :as jwt]
   [clj-time.core :as time]
   [leihs.core.core :refer [keyword str presence]]))

(defn prepare-key-str [s]
  (->> (-> s (clojure.string/split #"\n"))
       (map clojure.string/trim)
       (map presence)
       (filter identity)
       (clojure.string/join "\n")))

(defn private-key! [s]
  (-> s prepare-key-str keys/str->private-key
      (or (throw (ex-info "Private key error!"
                          {:status 500})))))

(defn public-key! [s]
  (-> s prepare-key-str keys/str->public-key
      (or (throw (ex-info "Public key error!"
                          {:status 500})))))

(defn create-signed-token [claims authentication-system]
  (let [priv-key (-> authentication-system :internal_private_key private-key!)]
    (jwt/sign
     (merge {:exp (time/plus (time/now) (time/seconds 90))
             :iat (time/now)}
            claims)
     priv-key {:alg :es256})))

(defn unsign-external-token [token authentication-system]
  (let [external-pub-key (-> authentication-system
                             :external_public_key
                             public-key!)]
    (jwt/unsign token external-pub-key {:alg :es256})))

(defn unsign-internal-token [token authentication-system]
  (let [internal-pub-key (-> authentication-system
                             :internal_public_key
                             public-key!)]
    (jwt/unsign token internal-pub-key {:alg :es256})))
