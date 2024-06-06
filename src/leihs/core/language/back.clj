(ns leihs.core.language.back
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [leihs.core.locale :refer [set-language-cookie]]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :refer [query update!]]
   [ring.util.response :refer [redirect]]))

(defn switch-language
  [{tx :tx
    {locale :locale} :form-params
    {referer "referer"} :headers
    authenticated-entity :authenticated-entity
    :as request}]
  (let [authenticated-user-id (-> authenticated-entity :id)
        lang (-> (sql/select :*)
                 (sql/from :languages)
                 (sql/where [:= :locale locale])
                 sql-format
                 (->> (jdbc/execute-one! tx)))]
    (if authenticated-user-id
      (do
        (assert (= (::jdbc/update-count
                    (update! tx
                             :users
                             {:language_locale locale}
                             ["id = ?" authenticated-user-id]))
                   1))
        (if (= (-> request :accept :mime) :json)
          {:status 200, :body (-> (sql/select :*)
                                  (sql/from :users)
                                  (sql/where [:= :id authenticated-user-id])
                                  sql-format
                                  (->> (query tx))
                                  first)}
          (redirect referer)))
      ; else just redirect and set cookie
      (-> (redirect referer)
          (set-language-cookie lang)))))

(defn routes [request]
  (case (:request-method request)
    :post (switch-language request)))
