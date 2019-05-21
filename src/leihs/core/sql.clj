(ns leihs.core.sql
  (:refer-clojure :exclude [format update set])
  (:require
    [honeysql.format :as format]
    [honeysql.helpers :as helpers :refer [build-clause]]
    [honeysql.types :as types]
    [honeysql.util :as util :refer [defalias]]

    [honeysql-postgres.helpers :as pg-helpers]
    [honeysql-postgres.format :as pg-format]

    ))

; regex
(defmethod format/fn-handler "~*" [_ field value]
  (str (format/to-sql field) " ~* " (format/to-sql value)))

; ilike
(defmethod format/fn-handler "~~*" [_ field value]
  (str (format/to-sql field) " ~~* " (format/to-sql value)))

(defn dedup-join [honeymap]
  (assoc honeymap :join
         (reduce #(let [[k v] %2] (conj %1 k v)) []
                 (clojure.core/distinct (partition 2 (:join honeymap))))))

(defn format
  "Calls honeysql.format/format with removed join duplications in sql-map."
  [sql-map & params-or-opts]
  (apply format/format [(dedup-join sql-map) params-or-opts]))


(defalias array types/array)
(defalias call types/call)
(defalias param types/param)
(defalias raw types/raw)

(defalias format-predicate format/format-predicate)
(defalias quote-identifier format/quote-identifier)

(defalias delete-from helpers/delete-from)
(defalias from helpers/from)
(defalias group helpers/group)
(defalias insert-into helpers/insert-into)
(defalias join helpers/join)
(defalias limit helpers/limit)
(defalias merge-join helpers/merge-join)
(defalias merge-left-join helpers/merge-left-join)
(defalias merge-select helpers/merge-select)
(defalias merge-where helpers/merge-where)
(defalias modifiers helpers/modifiers)
(defalias offset helpers/offset)
(defalias order-by helpers/order-by)
(defalias update helpers/update)
(defalias select helpers/select)
(defalias set helpers/sset)
(defalias values helpers/values)
(defalias where helpers/where)
(defalias with helpers/with)

(defalias do-nothing pg-helpers/do-nothing)
(defalias do-update-set pg-helpers/do-update-set)
(defalias do-update-set! pg-helpers/do-update-set!)
(defalias on-conflict pg-helpers/on-conflict)
(defalias on-conflict-constraint pg-helpers/on-conflict-constraint)
(defalias returning pg-helpers/returning)
(defalias upsert pg-helpers/upsert)

