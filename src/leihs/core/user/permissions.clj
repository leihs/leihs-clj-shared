(ns leihs.core.user.permissions
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [leihs.core.sql :as sql]))

(defn- access-rights-sqlmap [user-id]
  (-> (sql/select :*)
      (sql/from :access_rights)
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
      (sql/join :inventory_pools
                [:= :inventory_pools.id :access_rights.inventory_pool_id])
      (sql/merge-where [:in :access_rights.role ["group_manager"
                                                 "lending_manager"
                                                 "inventory_manager"]])
      (sql/select :inventory_pools.*)
      sql/format
      (->> (log/spy :info))
      (->> (jdbc/query tx))))
