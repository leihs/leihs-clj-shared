(ns leihs.core.database.helpers
  (:require [leihs.core.sql :as sql]
            [clojure.java.jdbc :as jdbc]))

(defn columns [tx table-name]
  (-> (sql/select :column_name)
      (sql/from :information_schema.columns)
      (sql/where [:= :table_name table-name])
      sql/format
      (->> (jdbc/query tx)
           (map (comp keyword
                      (partial str table-name ".")
                      :column_name)))))
