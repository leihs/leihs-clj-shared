(ns leihs.core.jdbc
  (:require [clojure.java.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc-next]
            [taoensso.timbre :refer [debug info warn error spy]]
            [leihs.core.db :as db]))

(defn get-query-fn [tx]
  (if (-> tx :options ::db/jdbc-next)
    jdbc-next/query
    jdbc/query))

(defn query
  ([tx sql-params]
   ((get-query-fn tx) tx sql-params {}))
  ([tx sql-params opts]
   ((get-query-fn tx) tx sql-params opts)))

