(ns leihs.core.auth.shared
  (:refer-clojure :exclude [str keyword])
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn access-rights [tx user-id]
  (-> (sql/select :role :inventory_pool_id)
      (sql/from :access_rights)
      (sql/where [:= :user_id user-id])
      sql-format
      (->> (jdbc-query tx))))

(defn password-hash
  [password tx]
  (->> [[:crypt [:cast password :text] [:raw "gen_salt('bf', 10)"]]
        :pw_hash]
       sql/select
       sql-format
       (jdbc-query tx)
       first
       :pw_hash))
