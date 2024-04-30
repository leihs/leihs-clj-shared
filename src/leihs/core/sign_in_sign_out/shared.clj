(ns leihs.core.sign-in-sign-out.shared
  (:require
   [clojure.string :as str]
   [honey.sql.helpers :as sql]))

(def authentication-systems-users-sql-expr
  [:or
   [:exists
    (-> (sql/select true)
        (sql/from :authentication_systems_users)
        (sql/where [:= :authentication_systems_users.user_id :users.id])
        (sql/where [:= :authentication_systems.id
                    :authentication_systems_users.authentication_system_id])
        (sql/where [:or
                    [:<> :authentication_systems.type "password"]
                    [:= :users.password_sign_in_enabled true]]))]
   [:exists
    (-> (sql/select true)
        (sql/from [:authentication_systems :asxs])
        (sql/join :authentication_systems_groups
                  [:and [:= :asxs.id :authentication_systems_groups.authentication_system_id]])
        (sql/join :groups_users [:and
                                 [:= :authentication_systems_groups.group_id :groups_users.group_id]
                                 [:= :authentication_systems_groups.group_id :groups_users.group_id]
                                 [:= :groups_users.user_id :users.id]])
        (sql/where [:= :asxs.id :authentication_systems.id]))]])

; FIXME: needs `DISTINCT`!!! (otherwise when user is nil, returns 1 entry per
; user in the DB (without login)!)
(def auth-system-user-base-query
  (-> (sql/from :authentication_systems :users)
      (sql/where [:= :authentication_systems.enabled true])
      (sql/where [:= :users.account_enabled true])
      (sql/where authentication-systems-users-sql-expr)
      (sql/order-by [:authentication_systems.priority :desc] :authentication_systems.id)))

(defn merge-identify-user [sqlmap unique-id]
  (sql/where sqlmap
             [:or
              [:= :users.org_id unique-id]
              [:= :users.login unique-id]
              [:= [:lower :users.email] (-> unique-id (or "") str/lower-case)]]))

(defn user-query-for-unique-id [user-unique-id]
  (-> (sql/select :*)
      (sql/from :users)
      (merge-identify-user user-unique-id)))

(defn auth-system-base-query-for-unique-id
  ([unique-id]
   (-> auth-system-user-base-query
       (merge-identify-user unique-id)))
  ([user-unique-id authentication-system-id]
   (-> (auth-system-base-query-for-unique-id user-unique-id)
       (sql/where [:= :authentication_systems.id authentication-system-id]))))

(defn auth-system-user-query [user-unique-id authentication-system-id]
  (-> (auth-system-base-query-for-unique-id user-unique-id authentication-system-id)
      (sql/select
       [[:row_to_json :authentication_systems] :authentication_system]
       [[:row_to_json :users] :user])))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
