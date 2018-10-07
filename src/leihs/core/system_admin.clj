(ns leihs.core.system-admin
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.core :refer [str keyword presence presence!]]
    [leihs.core.sql :as sql]))

(def system-admin-sql-expr
  [:or
   [:exists
    (-> (sql/select true)
        (sql/from :system_admin_users)
        (sql/merge-where [:= :users.id :system_admin_users.user_id]))]
   [:exists
    (-> (sql/select true)
        (sql/from :system_admin_groups)
        (sql/merge-join :groups [:= :groups.id :system_admin_groups.group_id])
        (sql/merge-join :groups_users [:and
                                       [:= :groups_users.group_id :groups.id]
                                       [:= :groups_users.user_id :users.id]]))]])
