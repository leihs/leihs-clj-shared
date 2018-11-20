(ns leihs.core.user.permissions
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [leihs.core.sql :as sql]))

(def MANAGER-ROLES ["group_manager"
                    "lending_manager"
                    "inventory_manager"])

(defn- access-rights-sqlmap [user-id]
  (-> (sql/select :*)
      (sql/from :access_rights)
      (sql/join :inventory_pools
                [:= :inventory_pools.id :access_rights.inventory_pool_id])
      (sql/merge-where [:= :inventory_pools.is_active true])
      (sql/merge-where [:= :access_rights.user_id user-id])
      (sql/merge-where [:= :access_rights.deleted_at nil])))

(defn borrow-access? [tx auth-entity]
  (-> auth-entity
      :id
      access-rights-sqlmap
      sql/format
      (->> (jdbc/query tx))
      empty?
      not))

(defn managed-inventory-pools [tx auth-entity]
  (-> auth-entity
      :id
      access-rights-sqlmap
      (sql/merge-where [:in :access_rights.role MANAGER-ROLES])
      (sql/select :inventory_pools.*)
      (sql/order-by [:inventory_pools.name :asc])
      sql/format
      (->> (jdbc/query tx))))

(defn manager? [tx auth-entity]
  (-> auth-entity
      :id
      access-rights-sqlmap
      (sql/merge-where [:in :access_rights.role MANAGER-ROLES])
      sql/format
      (->> (jdbc/query tx))
      empty?
      not))
