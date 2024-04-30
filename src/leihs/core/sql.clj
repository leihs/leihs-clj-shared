(ns leihs.core.sql
  (:require
   [clojure.string :as string]
   [honey.sql :refer [format format-expr] :rename {format sql-format} :as sql]
   [honey.sql.helpers :as sql-help]
   [leihs.core.core :refer [presence]]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(defn expand-to-tsquery-calls [terms]
  (let [tsquery-calls (->> terms
                           (map #(sql/format-expr [:to_tsquery %]))
                           (map first))]
    (str "(" (string/join " && " tsquery-calls) ")")))

(sql/register-fn! (keyword "@@")
                  (fn [op [field term]]
                    (let [terms-params (->> (string/split term #"\s+")
                                            (map presence)
                                            (filter identity))
                          [sql-field & params-field] (sql/format-expr [:to_tsvector field])]
                      (-> [(str sql-field
                                " "
                                (sql/sql-kw op)
                                " "
                                (expand-to-tsquery-calls terms-params))]
                          (into params-field)
                          (into terms-params)))))

(sql/register-fn! :is-null
                  (fn [op [a]]
                    (let [[sql-a & params-a] (sql/format-expr a)]
                      (-> [(str sql-a " " (sql/sql-kw :is-null))]
                          (into params-a)))))

(sql/register-fn! :is-not-null
                  (fn [op [a]]
                    (let [[sql-a & params-a] (sql/format-expr a)]
                      (-> [(str sql-a " " (sql/sql-kw :is-not-null))]
                          (into params-a)))))

(sql/register-op! (keyword "%"))
(sql/register-op! (keyword "~~*"))
(sql/register-op! (keyword "~*"))
(sql/register-op! (keyword "<@"))
(sql/register-op! (keyword "@>"))
