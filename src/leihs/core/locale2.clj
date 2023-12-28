(ns leihs.core.locale2
  (:require
    ;; all needed imports
    [compojure.core :as cpj]
    [honey.sql :refer [format] :rename {format sql-format}]
    [honey.sql.helpers :as sql]
    [leihs.core.paths :refer [path]]
    [next.jdbc :as jdbc]
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
  [{tx :tx-next
    {locale :locale} :form-params
    {referer :referer} :headers
    :as request}]
  (-> (redirect referer)
      (set-language-cookie (-> (sql/select :*)
                               (sql/from :languages)
                               (sql/where [:= locale :locale])
                               sql-format
                               (->> jdbc/execute-one! tx)))))

(defn delete-language-cookie [response]
  (set-language-cookie response nil 0))

(def get-active-languages-sqlmap
  (-> (sql/select :*)
      (sql/from :languages)
      (sql/where [:= :active true])))

(defn get-active-languages [tx]
  (-> get-active-languages-sqlmap
      sql-format
      (->> (jdbc/execute! tx))))

(defn get-active-lang [tx locale]
  (-> get-active-languages-sqlmap
      (sql/where [:= :locale locale])
      sql-format
      (->> (jdbc/execute-one! tx))))

(defn get-user-db-language [request]
  (let [tx (:tx-next request)
        auth-entity (:authenticated-entity request)
        languages (get-active-languages tx)]
    (->> languages
         (filter #(= (:locale %) (:language_locale auth-entity)))
         first)))

(defn get-selected-language [request]
  (let [tx (:tx-next request)
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
  [{:keys [tx tx-next] :as request} response user]
  (let [cookie-locale (-> request
                          :cookies
                          (get "leihs-user-locale")
                          :value)
        cookie-language (get-active-lang tx-next cookie-locale)]
    (cond cookie-language
          (jdbc/execute! tx-next (-> (sql/update :users)
                                     (sql/set {:language_locale (:locale cookie-language)})
                                     (sql/where [:= :id [:cast (:id user) :uuid]])
                                     sql-format))

          (when-let [user-locale (user :language_locale)]
            (not (get-active-lang tx-next user-locale)))

          (jdbc/execute! tx-next (-> (sql/update :users)
                                     (sql/set {:language_locale nil})
                                     (sql/where [:= :id [:cast (:id user) :uuid]])
                                     sql-format)))
    (delete-language-cookie response)))

(defn wrap [handler]
  (fn [request]
    (-> request
        set-user-language
        handler)))

(def routes
  (cpj/routes
    (cpj/POST (path :language) [] #'redirect-back-with-language-cookie)))
