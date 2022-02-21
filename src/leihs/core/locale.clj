(ns leihs.core.locale
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [leihs.core.paths :refer [path]]
            [leihs.core.sql :as sql]
            [compojure.core :as cpj]
            [ring.util.response :refer [redirect]]))

(defn set-language-cookie
  ([response language]
   (set-language-cookie response language (* 10 356 24 60 60)))
  ([response language max-age]
   (assoc-in response
             [:cookies "leihs-user-locale"]
             {:value (some-> language :locale),
              :path "/",
              :max-age max-age})))

(defn redirect-back-with-language-cookie
  [{tx :tx
    {locale :locale} :form-params
    {referer :referer} :headers
    :as request}]
  (-> (redirect referer)
      (set-language-cookie (jdbc/get-by-id tx :languages locale :locale))))

(defn delete-language-cookie [response]
  (set-language-cookie response nil 0))

(def get-active-languages-sqlmap
  (-> (sql/select :*)
      (sql/from :languages)
      (sql/where [:= :active true])))

(defn get-active-languages [tx]
  (-> get-active-languages-sqlmap
      sql/format
      (->> (jdbc/query tx))))

(defn get-active-lang [tx locale]
  (-> get-active-languages-sqlmap
      (sql/merge-where [:= :locale locale])
      sql/format
      (->> (jdbc/query tx))
      first))

(defn get-user-db-language [request]
  (let [tx (:tx request)
        auth-entity (:authenticated-entity request)
        languages (get-active-languages tx)]
    (->> languages
         (filter #(= (:locale %) (:language_locale auth-entity)))
         first)))

(defn get-selected-language [request]
  (let [tx (:tx request)
        languages (get-active-languages tx)
        default-language (->> languages
                              (filter :default)
                              first)
        user-db-language (get-user-db-language request)
        locale (-> request
                   :cookies
                   (get "leihs-user-locale")
                   :value)
        language-from-cookie (get-active-lang tx locale)]
    (or user-db-language
        language-from-cookie
        default-language)))

(defn set-user-language [request]
  (let [language (get-selected-language request)]
    (assoc request :leihs-user-language language)))

(defn setup-language-after-sign-in
  [{:keys [tx] :as request} response user]
  (let [cookie-locale (-> request
                          :cookies
                          (get "leihs-user-locale")
                          :value)
        cookie-language (get-active-lang tx cookie-locale)]
    (cond cookie-language
          (jdbc/update!
            tx
            :users
            {:language_locale (:locale cookie-language)}
            ["id = ?" (:id user)])
          (when-let [user-locale (user :language_locale)]
            (not (get-active-lang tx user-locale)))
          (jdbc/update!
            tx
            :users
            {:language_locale nil}
            ["id = ?" (:id user)]))
    (delete-language-cookie response)))

(defn wrap [handler]
  (fn [request]
    (-> request
        set-user-language
        handler)))

(def routes
  (cpj/routes
    (cpj/POST (path :language) [] #'redirect-back-with-language-cookie)))
