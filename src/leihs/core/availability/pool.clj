(ns leihs.core.availability.pool
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [java-time :as jt]
   [leihs.core.core :refer [detect]]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]))

(def workday-days
  [:monday :tuesday :wednesday :thursday :friday :saturday :sunday])

(def workday-columns
  (concat workday-days
          (map #(keyword (str (name %) "_orders_processing")) workday-days)))

(defn get-workdays [tx pool-id]
  (-> (apply sql/select workday-columns)
      (sql/from :workdays)
      (sql/where [:= :inventory_pool_id pool-id])
      sql-format
      (->> (jdbc-query tx))
      first))

(defn get-holidays [tx pool-id]
  (-> (sql/select :start_date :end_date :name :orders_processing)
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

(defn orders-processing-day?
  "Returns true if the pool processes orders on date's day of week.
  pool must have the `<day>_orders_processing` boolean keys."
  [date pool]
  (-> date
      .getDayOfWeek
      .toString
      .toLowerCase
      (str "_orders_processing")
      keyword
      pool))

(defn orders-processing?
  "Returns true if the pool processes orders on date, considering both the
  weekday flag and, if date falls on a holiday, that holiday's flag.
  pool must have the `<day>_orders_processing` boolean keys and :holidays."
  [date pool]
  (let [date* (jt/local-date date)]
    (and (orders-processing-day? date* pool)
         (if-let [holiday (get-holiday date* pool)]
           (:orders_processing holiday)
           true))))

(defn step-orders-processing-days
  "Steps from start-date via step (jt/plus or jt/minus) until n
  orders-processing days have been passed, returning the resulting date.
  Closed (non-workday or holiday) days are skipped over without counting,
  unless they are themselves orders-processing days, in which case they do
  count. n<=0 is a no-op, returning start-date unchanged regardless of pool
  (so pool may be nil then). pool must otherwise have weekday,
  `<day>_orders_processing` and :holidays keys."
  [start-date n pool step]
  (if (<= n 0)
    start-date
    (loop [date start-date, remaining n]
      (cond (close-time? date pool)
            (recur (step date (jt/days 1))
                   (if (orders-processing? date pool) (dec remaining) remaining))

            (pos? remaining)
            (recur (step date (jt/days 1)) (dec remaining))

            :else date))))
