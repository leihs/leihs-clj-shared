(ns leihs.core.sql
  (:refer-clojure :exclude [format update set])
  (:require
    [honeysql.core :as core]
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

; arrays: contains
(defmethod format/fn-handler "@>" [_ array1 array2]
  (str (format/to-sql array1) " @> " (format/to-sql array2)))
; arrays: is contained by
(defmethod format/fn-handler "<@" [_ array1 array2]
  (str (format/to-sql array1) " <@ " (format/to-sql array2)))
; arrays: overlaps
(defmethod format/fn-handler "&&" [_ array1 array2]
  (str (format/to-sql array1) " && " (format/to-sql array2)))

(defn dedup-join [honeymap]
  (assoc honeymap :join
         (reduce #(let [[k v] %2] (conj %1 k v)) []
                 (clojure.core/distinct (partition 2 (:join honeymap))))))

(defn format
  "Calls honeysql.format/format with removed join duplications in sql-map."
  [sql-map & params-or-opts]
  (apply format/format [(dedup-join sql-map) params-or-opts]))

(defalias qualify core/qualify)

(defalias array types/array)
(defalias call types/call)
(defalias inline types/inline)
(defalias param types/param)
(defalias raw types/raw)

(defalias format-predicate format/format-predicate)
(defalias quote-identifier format/quote-identifier)

(defalias delete-from helpers/delete-from)
(defalias from helpers/from)
(defalias group helpers/group)
(defalias having helpers/having)
(defalias insert-into helpers/insert-into)
(defalias join helpers/join)
(defalias limit helpers/limit)
(defalias merge-from helpers/merge-from)
(defalias merge-join helpers/merge-join)
(defalias merge-left-join helpers/merge-left-join)
(defalias merge-order-by helpers/merge-order-by)
(defalias merge-select helpers/merge-select)
(defalias merge-where helpers/merge-where)
(defalias modifiers helpers/modifiers)
(defalias offset helpers/offset)
(defalias order-by helpers/order-by)
(defalias select helpers/select)
(defalias set helpers/sset)
(defalias update helpers/update)
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

