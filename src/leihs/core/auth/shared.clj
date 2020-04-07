(ns leihs.core.auth.shared
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.core :refer [str keyword presence presence!]]
    [leihs.core.sql :as sql]

    [clojure.java.jdbc :as jdbc]

    [logbug.catcher :as catcher]
    [logbug.debug :as debug]))


(defn access-rights [tx user-id]
  (-> (sql/select :role :inventory_pool_id)
      (sql/from :access_rights)
      (sql/merge-where [:= :user_id user-id])
      sql/format
      (->> (jdbc/query tx))))
