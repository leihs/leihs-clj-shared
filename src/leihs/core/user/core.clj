(ns leihs.core.user.core
  (:require 
    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]
    [leihs.core.paths :refer [path]]
    [leihs.core.sql :as sql]
    [ring.util.response :refer [redirect]]))

(defn wrap-me-id 
  ([handler]
   (fn [request]
     (wrap-me-id handler request)))
  ([handler request]
   (handler
     (if (= "me" (-> request :route-params :user-id))
       (assoc-in request [:route-params :user-id]
                 (-> request :authenticated-entity :user_id))
       request))))

(defn update-user
  [{tx :tx
    {user-id :user-id} :route-params
    {locale :locale} :form-params
    {referer "referer"} :headers
    :as request}]
  (when user-id
    (assert (= (jdbc/update! tx
                             :users
                             {:language_locale locale}
                             ["id = ?" user-id])
               '(1))))
  (if (= (-> request :accept :mime) :json)
    {:status 200, :body (-> (sql/select :*)
                            (sql/from :users)
                            (sql/where [:= :id user-id])
                            sql/format
                            (->> (jdbc/query tx))
                            first)}
    (redirect referer)))

(def routes
  (cpj/routes
    (cpj/POST (path :my-user) [] (-> update-user wrap-me-id))))
