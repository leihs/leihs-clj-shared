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
                        [:= :authentication_systems.type "password"]
                        (sql/call
                          := :authentication_systems_users.data
                          (sql/call :crypt pw :authentication_systems_users.data))])
      sql/format))
