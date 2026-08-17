(ns leihs.core.availability.core
  (:require
   [java-time :as t]
   [leihs.core.availability.changes :as ch]
   [leihs.core.availability.pool :as pool]
   [leihs.core.availability.queries :as q]))

(defn- min-quantity-summed-for-groups
  "changes is a sparse timeline: {date {group-id {:in-quantity n, ...}}},
  quantity holding constant between consecutive dated entries. Returns the
  worst (lowest) quantity across [from-date, to-date], since a booking
  spanning the whole range is only as available as its tightest day.
  E.g. changes = {day1 {:general {:in-quantity 5}}
                  day5 {:general {:in-quantity 2}}
                  day10 {:general {:in-quantity 4}}}
  for from-date=day3, to-date=day8: day1 is the anchor still in effect at
  day3, day5 falls inside the range -> considers [5 2] -> returns 2."
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
  "changes/group-ids/pool-config for one model+pool+user, to fetch once and
  reuse across many quantity checks (e.g. a calendar's per-day loop) instead
  of every check re-querying them itself."
  [tx model-id user-id pool-id exclude-res-ids pickup-location-id]
  {:changes (ch/main tx model-id pool-id exclude-res-ids)
   :group-ids (concat [:general] (q/get-user-group-ids tx user-id))
   :pool-config (when pickup-location-id (q/get-pool-config tx pool-id))})

(defn available-quantity-in-range
  "Same as maximum-available-in-pool-and-period-summed-for-groups, given a
  context from fetch-context instead of tx/ids. For a single candidate date
  use available-quantity-for-prospective-start instead -- widening both
  directions from the same point double-counts."
  [{:keys [changes group-ids pool-config]} start-date end-date]
  (let [before-days (or (:transfer_buffer_before_pick_up pool-config) 0)
        after-days (or (:transfer_buffer_after_drop_off pool-config) 0)
        start-date* (ch/local-date start-date)
        end-date* (ch/local-date end-date)
        widened-start (pool/step-orders-processing-days start-date* before-days
                                                        pool-config t/minus)
        widened-end (pool/step-orders-processing-days end-date* after-days
                                                      pool-config t/plus)]
    (min-quantity-summed-for-groups changes group-ids widened-start
                                    widened-end)))

(defn available-quantity-for-prospective-start
  "Same as maximum-available-for-prospective-start-summed-for-groups, given a
  context from fetch-context instead of tx/ids."
  [{:keys [changes group-ids pool-config]} date]
  (let [before-days (or (:transfer_buffer_before_pick_up pool-config) 0)
        date* (ch/local-date date)
        widened-start (pool/step-orders-processing-days date* before-days
                                                        pool-config t/minus)]
    (min-quantity-summed-for-groups changes group-ids widened-start date*)))

(defn available-quantity-for-prospective-end
  "Returns the maximum available quantity for a model in a single inventory pool
  if date were used as the end of a new reservation via a pickup-location-id, given a
  context from fetch-context. Widens only forward from date by the pool's
  after-drop-off transfer buffer -- the lead time needed to move the item back from the
  alternative pickup location once dropped off -- symmetric to
  available-quantity-for-prospective-start's backward widening. We don't know yet whether
  a given day will end up being someone's start or end -- a 1-day booking is both at
  once, needing both buffers from that same day -- so this must always be combined with
  available-quantity-for-prospective-start via min, never used alone."
  [{:keys [changes group-ids pool-config]} date]
  (let [after-days (or (:transfer_buffer_after_drop_off pool-config) 0)
        date* (ch/local-date date)
        widened-end (pool/step-orders-processing-days date* after-days
                                                      pool-config t/plus)]
    (min-quantity-summed-for-groups changes group-ids date* widened-end)))

(defn available-quantity-for-prospective-day
  "min of available-quantity-for-prospective-start and
  available-quantity-for-prospective-end -- whether date is usable at all for a new
  booking via a pickup-location-id, regardless of whether it ends up being used as that
  booking's start or end."
  [context date]
  (min (available-quantity-for-prospective-start context date)
       (available-quantity-for-prospective-end context date)))

(defn maximum-available-in-pool-and-period-summed-for-groups
  "Returns the maximum available quantity for a model in a single inventory pool
  over the given date range, summed across all entitlement groups the user belongs to.
  When pickup-location-id is given, the range is widened by the pool's transfer
  buffers, reflecting the buffer the prospective alternative-location booking itself
  would need: backward from start-date by the before-pick-up buffer, forward from
  end-date by the after-drop-off buffer. Only sound for a genuine candidate
  reservation span (start-date and end-date are its real start and end) -- for a
  single candidate date, use maximum-available-for-prospective-start-summed-for-groups
  instead, since widening both directions from the same point double-counts.
  For repeated checks, prefer fetch-context + available-quantity-in-range."
  ([tx model-id user-id start-date end-date pool-id]
   (maximum-available-in-pool-and-period-summed-for-groups
    tx model-id user-id start-date end-date pool-id nil nil))

  ([tx model-id user-id start-date end-date pool-id exclude-res-ids]
   (maximum-available-in-pool-and-period-summed-for-groups
    tx model-id user-id start-date end-date pool-id exclude-res-ids nil))

  ([tx model-id user-id start-date end-date pool-id exclude-res-ids pickup-location-id]
   (-> (fetch-context tx model-id user-id pool-id exclude-res-ids
                      pickup-location-id)
       (available-quantity-in-range start-date end-date))))

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

(defn maximum-available-for-prospective-start-summed-for-groups
  "Returns the maximum available quantity for a model in a single inventory pool
  if date were used as the start of a new reservation via pickup-location-id.
  Widens only backward from date by the pool's before-pick-up transfer buffer --
  the lead time needed to move an item from the main warehouse out to the
  alternative pickup location -- never forward, since a single candidate date
  is not also an end date. Intended for per-day calendar display, where each
  day is checked independently as a potential start.
  For repeated checks, prefer fetch-context +
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
