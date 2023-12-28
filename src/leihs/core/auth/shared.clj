(ns leihs.core.auth.shared
  (:refer-clojure :exclude [keyword str])
  (:require
    ;; all needed imports
    [honey.sql :refer [format] :rename {format sql-format}]
    [honey.sql.helpers :as sql]
    [next.jdbc :as jdbc]
    ))

(defn access-rights [tx user-id]
  (-> (sql/select :role :inventory_pool_id)
      (sql/from :access_rights)
      (sql/where [:= :user_id [:cast user-id :uuid]])
      sql-format
      (->> (jdbc/execute-one! tx))))

(defn password-hash
  [password tx]
  (->> [[:crypt [[:cast password :text]]
         [[:raw "gen_salt('bf', 10)"]]] :pw_hash]
       sql/select
       sql-format
       (jdbc/execute-one! tx)
       :pw_hash))
