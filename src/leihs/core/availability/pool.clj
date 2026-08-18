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
  "Holiday map covering date, or nil."
  [date pool]
  (detect #(->> (dates-range (jt/local-date (:start_date %))
                             (jt/local-date (:end_date %)))
                (some #{date}))
          (:holidays pool)))

(defn working-day?
  "True if date is a working day for the pool."
  [date pool]
  (-> date .getDayOfWeek .toString .toLowerCase keyword pool))

(defn close-time?
  "True if the pool is closed on date (non-workday or holiday)."
  [date pool]
  (let [date* (jt/local-date date)]
    (or (not (working-day? date* pool))
        (some? (get-holiday date* pool)))))

(defn orders-processing-day?
  "True if the pool processes orders on date's day of week."
  [date pool]
  (-> date
      .getDayOfWeek
      .toString
      .toLowerCase
      (str "_orders_processing")
      keyword
      pool))

(defn orders-processing?
  "True if the pool processes orders on date, factoring in any holiday."
  [date pool]
  (let [date* (jt/local-date date)]
    (and (orders-processing-day? date* pool)
         (if-let [holiday (get-holiday date* pool)]
           (:orders_processing holiday)
           true))))

(defn step-orders-processing-days
  "Steps from start-date via step until n orders-processing days have passed,
  returning the resulting date. Non-processing days don't count but can still
  end up as the result. n<=0 is a no-op."
  [start-date n pool step]
  (loop [date start-date, remaining n]
    (if (pos? remaining)
      (recur (step date (jt/days 1))
             (if (orders-processing? date pool) (dec remaining) remaining))
      date)))

(defn extend-through-idle-run
  "If boundary itself is a non-processing day, keeps stepping until it
  reaches the next processing day, bridging idle runs (e.g. weekends) that
  would otherwise sit as a free pocket between two buffer zones."
  [boundary pool step]
  (if (orders-processing? boundary pool)
    boundary
    (loop [date boundary]
      (let [next-date (step date (jt/days 1))]
        (if (orders-processing? next-date pool)
          next-date
          (recur next-date))))))
