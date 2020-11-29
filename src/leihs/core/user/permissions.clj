(ns leihs.core.user.permissions
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as log]
    [leihs.core.sql :as sql]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]))

(def MANAGER-ROLES ["group_manager"
                    "lending_manager"
                    "inventory_manager"])

(def CUSTOMER-ROLES (conj MANAGER-ROLES "customer"))

(def inventory-access-base-query
  (-> (sql/from :inventory_pools)
      (sql/merge-where [:= :inventory_pools.is_active true])))

(defn user-direct-access-right-subquery [user-id roles]
  (-> (sql/select :1)
      (sql/from :direct_access_rights)
      (sql/merge-where [:= :inventory_pools.id :direct_access_rights.inventory_pool_id])
      (sql/merge-where [:= :direct_access_rights.user_id user-id])
      (sql/merge-where [:in :direct_access_rights.role roles])))

(defn user-group-access-right-subquery [user-id roles]
  (-> (sql/select :1)
      (sql/from :group_access_rights)
      (sql/merge-where [:= :inventory_pools.id :group_access_rights.inventory_pool_id])
      (sql/merge-where [:in :group_access_rights.role roles])
      (sql/merge-join :groups [:= :groups.id  :group_access_rights.group_id])
      (sql/merge-join :groups_users [:= :groups_users.group_id :groups.id])
      (sql/merge-where [:= :groups_users.user_id user-id])))

(defn borrow-access? [tx {user-id :id}]
  (-> inventory-access-base-query
      (sql/select :1)
      (sql/merge-where
        [:or
         {:exists (user-direct-access-right-subquery user-id CUSTOMER-ROLES)}
         {:exists (user-group-access-right-subquery user-id CUSTOMER-ROLES)}])
      sql/format
      (->> (jdbc/query tx))
      seq boolean))

(defn managed-inventory-pools-query [user-id]
  (-> inventory-access-base-query
      (sql/select :inventory_pools.*)
      (sql/merge-where
        [:or
         {:exists (user-direct-access-right-subquery user-id MANAGER-ROLES)}
         {:exists (user-group-access-right-subquery user-id MANAGER-ROLES)}])
      (sql/order-by :inventory_pools.name)))

(defn managed-inventory-pools [tx {user-id :id}]
  (-> (managed-inventory-pools-query user-id)
      sql/format
      (->> (jdbc/query tx))))

(defn manager? [tx {user-id :id}]
  (-> inventory-access-base-query
      (sql/select :1)
      (sql/merge-where
        [:or
         {:exists (user-direct-access-right-subquery user-id MANAGER-ROLES)}
         {:exists (user-group-access-right-subquery user-id MANAGER-ROLES)}])
      sql/format
      (->> (jdbc/query tx))
      seq boolean))

(defn sysadmin? [tx auth-entity]
  (-> (sql/select :*)
      (sql/from :system_admin_users)
      (sql/where [:= :user_id (:id auth-entity)])
      sql/format
      (->> (jdbc/query tx))
      empty?
      not))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
