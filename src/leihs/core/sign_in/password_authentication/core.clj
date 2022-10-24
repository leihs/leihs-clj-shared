(ns leihs.core.sign-in.password-authentication.core
  (:refer-clojure :exclude [str keyword])
  (:require
    [honey.sql :refer [format] :rename {format sql-format}]
    [honey.sql.helpers :as sql]
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.db :as db]
    [next.jdbc :as jdbc]
    [next.jdbc.sql :refer [query] :rename {query jdbc-query}]
    [taoensso.timbre :refer [error warn info debug spy]]
    ))


; for honey2 some similar methods as in leihs.core.sign-in.shared
; TODO clean up and consilidate when removing honey1

(def user-select
  (sql/select :users.id
              :users.admin_protected
              :users.badge_id
              :users.email
              :users.firstname
              :users.is_admin
              :users.is_system_admin
              :users.lastname
              :users.login
              :users.org_id
              :users.organization
              :users.secondary_email
              :users.system_admin_protected
              [:users.id :user_id]
              ))

(defn where-unique-user [query user-uid]
  (sql/where
    query
    [:or
     [:= [:lower [:concat :users.org_id "|" :users.organization]] [:lower user-uid]]
     [:= [:lower :users.login] [:lower user-uid]]
     [:= [:lower :users.email] [:lower user-uid]]]))

(defn join-password-auth [query]
  (-> query
      (sql/select [:authentication_systems_users.data :password_hash])
      (sql/join :authentication_systems_users
                [:= :authentication_systems_users.user_id :users.id])
      (sql/join :authentication_systems
                [:= :authentication_systems.id
                 :authentication_systems_users.authentication_system_id])
      (sql/where [:= :authentication_systems.type "password"])
      (sql/where [:<> nil :authentication_systems_users.data])
      (sql/where [:= :users.account_enabled true])
      (sql/where [:= :users.password_sign_in_enabled true])))

(defn user-query [user-uid]
  (-> user-select
      (sql/from :users)
      (where-unique-user user-uid)
      (join-password-auth)))


(defn check-password
  [password password-hash &{:keys [tx]
                            :or {tx (db/get-ds-next)}}]
  (-> (sql/select [[:= password-hash [:crypt password password-hash]]
                   :password_is_ok])
      (sql-format)
      (#(jdbc/execute-one! tx % db/builder-fn-options))
      :password_is_ok))


(defn password-checked-user
  "Returns the user iff the account exists, is allowed to sign in
  and the password matches. Returns nil if the user is not found, has no
  password or is not allowed to sign in. Returns false if the user is
  found but the password does not match.
  The tx parameter is optional and in general not to be used
  since this never causes a mutation and the db operation is costly. "
  [user-uid password & {:keys [tx]
                        :or {tx (db/get-ds-next)}}]
  (when-let [user (-> (user-query user-uid)
                      (sql-format)
                      (#(jdbc/execute-one! tx % db/builder-fn-options)))]
    (and (check-password password (:password_hash user))
         (dissoc user :password_hash))))
