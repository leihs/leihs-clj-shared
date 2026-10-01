(ns leihs.core.availability.core
  (:require
   [java-time :as t]
   [leihs.core.availability.changes :as ch]
   [leihs.core.availability.queries :as q]))

(defn summed-for-groups
  "Minimum over the changes of the summed in-quantities of the groups (all
  groups when `group-ids` is nil). Not floored, negative = overbooked."
  [changes group-ids]
  (->> (vals changes)
       (map (fn [allocs]
              (->> (cond-> allocs group-ids (select-keys group-ids))
                   vals
                   (map :in-quantity)
                   (apply +))))
       (apply min)))

(defn- intervals
  "Splits [start, end] at the change dates into intervals of constant
  availability."
  [changes start end]
  (let [dates (->> (keys changes)
                   (filter #(and (t/after? % start) (not (t/after? % end))))
                   sort)]
    (map (fn [from to-next]
           [from (if to-next (t/minus to-next (t/days 1)) end)])
         (cons start dates)
         (concat dates [nil]))))

(defn booking-calendar
  "Per-date quantity for the groups and total quantity over all groups, from
  start to end. Not floored. Dates before today get 0."
  [changes group-ids start end]
  (let [today (ch/local-date)
        start* (if (t/before? start today) today start)
        past (when (t/before? start today)
               (->> (ch/explode-date-range start (t/min end (t/minus today (t/days 1))))
                    (map #(hash-map :date % :quantity 0 :total_quantity 0))))
        upcoming (when-not (t/before? end start*)
                   (mapcat (fn [[from to]]
                             (let [inner (ch/between changes from to)
                                   quantity (summed-for-groups inner group-ids)
                                   total (summed-for-groups inner nil)]
                               (->> (ch/explode-date-range from to)
                                    (map #(hash-map :date %
                                                    :quantity quantity
                                                    :total_quantity total)))))
                           (intervals changes start* end)))]
    (concat past upcoming)))

(defn maximum-available-in-pool-and-period-summed-for-groups
  "Returns the maximum available quantity for a model in a single inventory pool
  over the given date range, summed across all entitlement groups the user belongs to."
  ([tx model-id user-id start-date end-date pool-id]
   (maximum-available-in-pool-and-period-summed-for-groups
    tx model-id user-id start-date end-date pool-id nil))

  ([tx model-id user-id start-date end-date pool-id exclude-res-ids]
   (let [changes (ch/main tx model-id pool-id exclude-res-ids)
         group-ids (cons :general (q/get-user-group-ids tx user-id))
         inner-changes (ch/between changes
                                   (ch/local-date start-date)
                                   (ch/local-date end-date))]
     (if (empty? inner-changes)
       0
       (max 0 (summed-for-groups inner-changes group-ids))))))

(defn maximum-available-in-period-summed-for-groups
  "Returns the total maximum available quantity for a model across multiple inventory pools."
  ([tx model-id user-id start-date end-date pool-ids]
   (maximum-available-in-period-summed-for-groups
    tx model-id user-id start-date end-date pool-ids nil))

  ([tx model-id user-id start-date end-date pool-ids exclude-res-ids]
   (->> pool-ids
        (map #(maximum-available-in-pool-and-period-summed-for-groups
               tx model-id user-id start-date end-date % exclude-res-ids))
        (apply +))))
