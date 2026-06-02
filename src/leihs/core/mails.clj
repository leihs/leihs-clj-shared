(ns leihs.core.mails
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]
   [taoensso.timbre :refer [debug warn]]))

(defn get-tmpl [tx name pool-id lang-locale]
  (-> (sql/select :subject :body)
      (sql/from :mail_templates)
      (sql/where [:= :name name])
      (sql/where [:= :inventory_pool_id pool-id])
      (sql/where [:= :language_locale lang-locale])
      sql-format
      (->> (jdbc-query tx))
      first))

(defn log-mail-failure [recipient-id e]
  (debug e)
  (warn (str "Error sending notification email to " recipient-id ": "
             (.getMessage e)
             " — user may need to be contacted by other means.")))
