(ns leihs.core.auth.shared
  (:refer-clojure :exclude [str keyword])
  (:require
    [clojure.java.jdbc :as jdbc]
    [honey.sql :refer [format] :rename {format sql-format}]
    [honey.sql.helpers :as h]
    [next.jdbc :as jdbc-next]
    [leihs.core.sql :as sql]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(defn access-rights ^:deprecated [tx user-id]
  (-> (sql/select :role :inventory_pool_id)
      (sql/from :access_rights)
      (sql/merge-where [:= :user_id user-id])
      sql/format
      (->> (jdbc/query tx))))

(defn access-rights-new [tx user-id]
  (-> (h/select :role :inventory_pool_id)
      (h/from :access_rights)
      (h/where [:= :user_id user-id])
      sql-format
      (->> (jdbc-next/execute! tx))))
