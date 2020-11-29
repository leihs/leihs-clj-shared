(ns leihs.core.system-admin
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.core :refer [str keyword presence presence!]]
    [leihs.core.sql :as sql]))

(def system-admin-sql-expr
  [:exists
   (-> (sql/select true)
       (sql/from :system_admin_users)
       (sql/merge-where [:= :users.id :system_admin_users.user_id]))])
