(ns leihs.core.auth.shared
  (:refer-clojure :exclude [str keyword])
  (:require
    [clojure.java.jdbc :as jdbc]
    [leihs.core.core :refer [str keyword presence presence!]]
    [leihs.core.sql :as sql]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(defn access-rights [tx user-id]
  (-> (sql/select :role :inventory_pool_id)
      (sql/from :access_rights)
      (sql/merge-where [:= :user_id user-id])
      sql/format
      (->> (jdbc/query tx))))

(defn password-hash
  [password tx]
  (->> [(sql/call :crypt
                  (sql/call :cast password :text)
                  (sql/raw "gen_salt('bf', 10)"))
        :pw_hash]
       sql/select
       sql/format
       (jdbc/query tx)
       first
       :pw_hash))
