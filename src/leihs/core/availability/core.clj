(ns leihs.core.availability.core
  (:require
   [java-time :as t]
   [leihs.core.availability.changes :as ch]
   [leihs.core.availability.pool :as pool]
   [leihs.core.availability.queries :as q]))

(defn- min-quantity-summed-for-groups
  "Worst (lowest) summed quantity across [from-date, to-date] in the sparse
  changes timeline."
  [changes group-ids from-date to-date]
  (let [inner-changes (ch/between changes from-date to-date)
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
           non-negative))))

(defn fetch-context
  "changes/group-ids/pool-config for one model+pool+user, fetched once for
  reuse across many quantity checks."
  [tx model-id user-id pool-id exclude-res-ids pickup-location-id]
  {:changes (ch/main tx model-id pool-id exclude-res-ids)
   :group-ids (concat [:general] (q/get-user-group-ids tx user-id))
   :pool-config (when pickup-location-id (q/get-pool-config tx pool-id))})

(defn- widen-and-bridge
  "Widens date by days and bridges any idle run at the edge; a zero-length
  buffer does neither."
  [date days pool step]
  (let [raw (pool/step-orders-processing-days date days pool step)]
    (if (pos? days)
      (pool/extend-through-idle-run raw pool step)
      raw)))

(defn available-quantity-in-range
  "Like maximum-available-in-pool-and-period-summed-for-groups, given a
  fetch-context context. For a single candidate date use
  available-quantity-for-prospective-start instead."
  [{:keys [changes group-ids pool-config]} start-date end-date]
  (let [before-days (or (:transfer_buffer_before_pick_up pool-config) 0)
        after-days (or (:transfer_buffer_after_drop_off pool-config) 0)
        start-date* (ch/local-date start-date)
        end-date* (ch/local-date end-date)
        widened-start (widen-and-bridge start-date* before-days pool-config
                                        t/minus)
        widened-end (widen-and-bridge end-date* after-days pool-config
                                      t/plus)]
    (min-quantity-summed-for-groups changes group-ids widened-start
                                    widened-end)))

(defn available-quantity-for-prospective-start
  "Like maximum-available-for-prospective-start-summed-for-groups, given a
  fetch-context context."
  [{:keys [changes group-ids pool-config]} date]
  (let [before-days (or (:transfer_buffer_before_pick_up pool-config) 0)
        date* (ch/local-date date)
        widened-start (widen-and-bridge date* before-days pool-config t/minus)]
    (min-quantity-summed-for-groups changes group-ids widened-start date*)))

(defn available-quantity-for-prospective-end
  "Widens forward from date by the after-drop-off buffer, symmetric to
  available-quantity-for-prospective-start's backward widening. Always
  combine with it via min -- never used alone."
  [{:keys [changes group-ids pool-config]} date]
  (let [after-days (or (:transfer_buffer_after_drop_off pool-config) 0)
        date* (ch/local-date date)
        widened-end (widen-and-bridge date* after-days pool-config t/plus)]
    (min-quantity-summed-for-groups changes group-ids date* widened-end)))

(defn available-quantity-for-prospective-day
  "Whether date is usable at all for a new booking, as either its start or
  end."
  [context date]
  (min (available-quantity-for-prospective-start context date)
       (available-quantity-for-prospective-end context date)))

(defn maximum-available-in-pool-and-period-summed-for-groups
  "Max available quantity for a model in a pool over a date range, summed
  across the user's entitlement groups. With pickup-location-id, widens by
  the pool's transfer buffers for a genuine candidate span. For a single
  candidate date use
  maximum-available-for-prospective-start-summed-for-groups instead. For
  repeated checks, prefer fetch-context + available-quantity-in-range."
  ([tx model-id user-id start-date end-date pool-id]
   (maximum-available-in-pool-and-period-summed-for-groups
    tx model-id user-id start-date end-date pool-id nil nil))

  ([tx model-id user-id start-date end-date pool-id exclude-res-ids]
   (maximum-available-in-pool-and-period-summed-for-groups
    tx model-id user-id start-date end-date pool-id exclude-res-ids nil))

  ([tx model-id user-id start-date end-date pool-id exclude-res-ids
    pickup-location-id]
   (-> (fetch-context tx model-id user-id pool-id exclude-res-ids
                      pickup-location-id)
       (available-quantity-in-range start-date end-date))))

(defn maximum-available-in-period-summed-for-groups
  "Total max available quantity for a model across multiple pools."
  ([tx model-id user-id start-date end-date pool-ids]
   (maximum-available-in-period-summed-for-groups
    tx model-id user-id start-date end-date pool-ids nil nil))

  ([tx model-id user-id start-date end-date pool-ids exclude-res-ids]
   (maximum-available-in-period-summed-for-groups
    tx model-id user-id start-date end-date pool-ids exclude-res-ids nil))

  ([tx model-id user-id start-date end-date pool-ids exclude-res-ids
    pickup-location-id]
   (->> pool-ids
        (map #(maximum-available-in-pool-and-period-summed-for-groups
               tx model-id user-id start-date end-date % exclude-res-ids
               pickup-location-id))
        (apply +))))

(defn maximum-available-for-prospective-start-summed-for-groups
  "Max available quantity for a model in a pool if date were used as the
  start of a new pickup-location-id reservation. Widens only backward, for
  per-day calendar display. For repeated checks, prefer fetch-context +
  available-quantity-for-prospective-start."
  ([tx model-id user-id date pool-id]
   (maximum-available-for-prospective-start-summed-for-groups
    tx model-id user-id date pool-id nil nil))

  ([tx model-id user-id date pool-id exclude-res-ids]
   (maximum-available-for-prospective-start-summed-for-groups
    tx model-id user-id date pool-id exclude-res-ids nil))

  ([tx model-id user-id date pool-id exclude-res-ids pickup-location-id]
   (-> (fetch-context tx model-id user-id pool-id exclude-res-ids
                      pickup-location-id)
       (available-quantity-for-prospective-start date))))
