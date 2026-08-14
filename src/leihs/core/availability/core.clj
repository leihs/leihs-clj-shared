(ns leihs.core.availability.core
  (:require
   [java-time :as t]
   [leihs.core.availability.changes :as ch]
   [leihs.core.availability.pool :as pool]
   [leihs.core.availability.queries :as q]))

(defn maximum-available-in-pool-and-period-summed-for-groups
  "Returns the maximum available quantity for a model in a single inventory pool
  over the given date range, summed across all entitlement groups the user belongs to.
  When pickup-location-id is given, the range is widened by the pool's transfer
  buffers, reflecting the buffer the prospective alternative-location booking itself
  would need."
  ([tx model-id user-id start-date end-date pool-id]
   (maximum-available-in-pool-and-period-summed-for-groups
    tx model-id user-id start-date end-date pool-id nil nil))

  ([tx model-id user-id start-date end-date pool-id exclude-res-ids]
   (maximum-available-in-pool-and-period-summed-for-groups
    tx model-id user-id start-date end-date pool-id exclude-res-ids nil))

  ([tx model-id user-id start-date end-date pool-id exclude-res-ids pickup-location-id]
   (let [changes (ch/main tx model-id pool-id exclude-res-ids)
         user-group-ids (q/get-user-group-ids tx user-id)
         group-ids (concat [:general] user-group-ids)
         pool-config (when pickup-location-id (q/get-pool-config tx pool-id))
         before-days (or (:transfer_buffer_before_pick_up pool-config) 0)
         after-days (or (:transfer_buffer_after_drop_off pool-config) 0)
         inner-changes (ch/between changes
                                   (pool/step-orders-processing-days (ch/local-date start-date)
                                                                     before-days
                                                                     pool-config
                                                                     t/minus)
                                   (pool/step-orders-processing-days (ch/local-date end-date)
                                                                     after-days
                                                                     pool-config
                                                                     t/plus))
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
    tx model-id user-id start-date end-date pool-ids nil nil))

  ([tx model-id user-id start-date end-date pool-ids exclude-res-ids]
   (maximum-available-in-period-summed-for-groups
    tx model-id user-id start-date end-date pool-ids exclude-res-ids nil))

  ([tx model-id user-id start-date end-date pool-ids exclude-res-ids pickup-location-id]
   (->> pool-ids
        (map #(maximum-available-in-pool-and-period-summed-for-groups
               tx model-id user-id start-date end-date % exclude-res-ids pickup-location-id))
        (apply +))))
