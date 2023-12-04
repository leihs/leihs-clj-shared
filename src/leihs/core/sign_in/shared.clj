(ns leihs.core.sign-in.shared
  (:refer-clojure :exclude [str keyword])
  (:require
   [clojure.string :as str]
   [leihs.core.core :refer [keyword str presence]]
   [leihs.core.paths :refer [path]]
   [leihs.core.sql :as sql]
   [logbug.debug :as debug]))

(def authentication-systems-users-sql-expr
  [:or
   [:exists
    (-> (sql/select true)
        (sql/from :authentication_systems_users)
        (sql/merge-where [:= :authentication_systems_users.user_id :users.id])
        (sql/merge-where [:= :authentication_systems.id
                          :authentication_systems_users.authentication_system_id])
        (sql/merge-where [:or
                          [:<> :authentication_systems.type "password"]
                          [:= :users.password_sign_in_enabled true]])
        (sql/merge-where [:= :users.account_enabled true]))]
   [:exists
    (-> (sql/select true)
        (sql/from [:authentication_systems :asxs])
        (sql/merge-where [:= :asxs.id :authentication_systems.id])
        (sql/merge-join :authentication_systems_groups
                        [:and [:= :asxs.id :authentication_systems_groups.authentication_system_id]])
        (sql/merge-join :groups_users [:and
                                       [:= :authentication_systems_groups.group_id :groups_users.group_id]
                                       [:= :authentication_systems_groups.group_id :groups_users.group_id]
                                       [:= :groups_users.user_id :users.id]])
        (sql/merge-where [:= :users.account_enabled true]))]])

; FIXME: needs `DISTINCT`!!! (otherwise when user is nil, returns 1 entry per
; user in the DB (without login)!)
(def auth-system-user-base-query
  (-> (sql/from :authentication_systems :users)
      (sql/merge-where authentication-systems-users-sql-expr)
      (sql/merge-where [:= :authentication_systems.enabled true])
      (sql/merge-join [:= :users.account_enabled true])
      (sql/order-by [:authentication_systems.priority :desc] :authentication_systems.id)))

;(-> auth-system-user-base-query sql/format)

(defn merge-identify-user [sqlmap unique-id]
  (sql/merge-where sqlmap
                   [:or
                    [:= :users.org_id unique-id]
                    [:= :users.login unique-id]
                    [:= (sql/raw "lower(users.email)") (-> unique-id (or "") str/lower-case)]]))

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
       (sql/merge-where [:= :authentication_systems.id authentication-system-id]))))

(comment
  (-> (auth-system-base-query-for-unique-id "mkmit")
      (sql/merge-select :*)
      sql/format))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
