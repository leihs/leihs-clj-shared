(ns leihs.core.password-authentication.back
  (:refer-clojure :exclude [str keyword])
  (:require [leihs.core.core :refer [keyword str presence]])
  (:require
    [leihs.core.sql :as sql]
    [leihs.core.auth.session :as session]

    [clojure.java.jdbc :as jdbc]
    [clojure.string :as str]))


(defn password-check-query [unique-id pw]
  (-> (sql/select :users.* [:users.id :user_id])
      (sql/from :users)
      (sql/merge-where  
        [:or 
         [:= (sql/call :lower (sql/call :trim :users.email)) 
          (sql/call :lower unique-id)]
         [:= :users.login unique-id]
         [:= :users.org_id unique-id]])
      (sql/merge-where [:= :users.account_enabled true])
      (sql/merge-where [:= :users.password_sign_in_enabled true])
      (sql/merge-join :authentication_systems_users
                      [:= :users.id
                       :authentication_systems_users.user_id])
      (sql/merge-join :authentication_systems
                      [:= :authentication_systems_users.authentication_system_id
                       :authentication_systems.id])
      (sql/merge-where [:and
                        [:<> :authentication_systems_users.data nil]
                        (sql/call
                          := :authentication_systems_users.data
                          (sql/call :crypt pw :authentication_systems_users.data))])
      sql/format))

(defn ring-handler
  [{tx :tx
    {email :email password :password} :body
    settings :settings
    :as request}]
  (if-let [user (->> [email password]
                     (apply password-check-query)
                     (jdbc/query tx)
                     first)]
    (let [user-session (session/create-user-session user request)]
      {:body user
       :status 200
       :cookies {leihs.core.constants/USER_SESSION_COOKIE_NAME
                 {:value (:token user-session)
                  :http-only true
                  :max-age (* 10 356 24 60 60)
                  :path "/"
                  :secure (:sessions_force_secure settings)}}})
    {:status 401
     :body (->> ["Password authentication failed!"
                 "Check your password and try again."
                 "Contact your leihs administrator if the problem persists."]
                (clojure.string/join " \n"))}))


