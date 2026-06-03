(ns leihs.core.availability.core
  (:require
   [leihs.core.availability.changes :as ch]
   [leihs.core.availability.queries :as q]))

(defn maximum-available-in-pool-and-period-summed-for-groups
  "Returns the maximum available quantity for a model in a single inventory pool
  over the given date range, summed across all entitlement groups the user belongs to."
  ([tx model-id user-id start-date end-date pool-id]
   (maximum-available-in-pool-and-period-summed-for-groups
    tx model-id user-id start-date end-date pool-id nil))

  ([tx model-id user-id start-date end-date pool-id exclude-res-ids]
   (let [changes (ch/main tx model-id pool-id exclude-res-ids)
         user-group-ids (q/get-user-group-ids tx user-id)
         group-ids (concat [:general] user-group-ids)
         inner-changes (ch/between changes
                                   (ch/local-date start-date)
                                   (ch/local-date end-date))
         non-negative #(if (neg? %) 0 %)]
     (if (empty? inner-changes)
       0
       (->> inner-changes
            vals
            (map (fn [allocs]
                   (-> allocs
                       (select-keys group-ids)
                       vals
                       (->> (map :in-quantity)
                            (apply +)))))
            (apply min)
            non-negative)))))

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
