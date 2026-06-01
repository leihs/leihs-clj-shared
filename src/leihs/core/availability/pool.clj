(ns leihs.core.availability.pool
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [java-time :as jt]
   [leihs.core.core :refer [detect]]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(def workday-columns
  [:monday :tuesday :wednesday :thursday :friday :saturday :sunday])

(defn get-workdays [tx pool-id]
  (-> (apply sql/select workday-columns)
      (sql/from :workdays)
      (sql/where [:= :inventory_pool_id pool-id])
      sql-format
      (->> (jdbc-query tx))
      first))

(defn get-holidays [tx pool-id]
  (-> (sql/select :start_date :end_date :name)
      (sql/from :holidays)
      (sql/where [:= :inventory_pool_id pool-id])
      (sql/where [:>= :end_date [:raw "CURRENT_DATE"]])
      (sql/order-by :start_date :end_date)
      sql-format
      (->> (jdbc-query tx))))

(defn dates-range [start end]
  (->> (jt/iterate jt/plus start (jt/days 1))
       (take-while #(or (jt/before? % end) (= % end)))))

(defn get-holiday
  "Returns the holiday map if date falls within any holiday, nil otherwise.
  pool must have :holidays key with a seq of {:start_date, :end_date} maps."
  [date pool]
  (detect #(->> (dates-range (jt/local-date (:start_date %))
                             (jt/local-date (:end_date %)))
                (some #{date}))
          (:holidays pool)))

(defn working-day?
  "Returns true if date is a working day for the pool.
  pool must have weekday boolean keys (:monday, :tuesday, ...)."
  [date pool]
  (-> date .getDayOfWeek .toString .toLowerCase keyword pool))

(defn close-time?
  "Returns true if the pool is closed on date (non-workday or holiday).
  pool must have weekday boolean keys and :holidays."
  [date pool]
  (let [date* (jt/local-date date)]
    (or (not (working-day? date* pool))
        (some? (get-holiday date* pool)))))
