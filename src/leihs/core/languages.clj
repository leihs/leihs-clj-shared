(ns leihs.core.languages
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(def base-sqlmap
  (-> (sql/select :languages.*)
      (sql/from :languages)
      (sql/where [:= :active true])
      (sql/order-by [:languages.name :asc])))

(defn get-by-locale [tx locale]
  (-> base-sqlmap
      (sql/where [:= :locale locale])
      sql-format
      (->> (jdbc-query tx))
      first))

(defn default [tx]
  (-> base-sqlmap
      (sql/where [:= :languages.default true])
      sql-format
      (->> (jdbc-query tx))
      first))

(defn- get-user-locale [tx user-id]
  (-> (sql/select :language_locale)
      (sql/from :users)
      (sql/where [:= :id user-id])
      sql-format
      (->> (jdbc-query tx))
      first
      :language_locale))

(defn get-the-one-to-use
  "Returns the language for user-id, falling back to the system default."
  [tx user-id]
  (or (some->> user-id (get-user-locale tx) (get-by-locale tx))
      (default tx)))
