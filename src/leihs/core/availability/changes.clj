(ns leihs.core.availability.changes
  (:require
   [clojure.set :as set]
   [com.rpl.specter :as s]
   [java-time :as t]
   [leihs.core.availability.allocations :as a]
   [leihs.core.availability.queries :as q]))

(def UTC-ZONE-ID (java.time.ZoneId/of "UTC"))

(defn local-date
  ([] (t/local-date (t/instant) UTC-ZONE-ID))
  ([date]
   (-> date
       t/local-date
       (.atStartOfDay UTC-ZONE-ID)
       .toLocalDate)))

(defn initial-group-quantity [tx entitlement pool-id]
  (let [max-possible-quantity (count (q/get-borrowable-items tx (:model_id entitlement) pool-id))]
    (min max-possible-quantity (:quantity entitlement))))

(defn init [tx entitlements pool-id]
  (let [entitlements-map
        (as-> entitlements <>
          (reduce #(assoc %1 (:entitlement_group_id %2) (initial-group-quantity tx %2 pool-id)) {} <>)
          (set/rename-keys <> {nil :general})
          (cond-> <> (nil? (:general <>)) (assoc :general 0)))
        initial-group-allocations
        (->> entitlements-map
             (map (fn [e-map]
                    [(first e-map) {:in-quantity (second e-map)
                                    :running-reservations []}]))
             (into {}))]
    {(local-date) initial-group-allocations}))

(def replacement-interval (t/months 1))

(defn late? [reservation]
  (and (-> reservation :returned_date nil?)
       (t/before? (-> reservation :end_date local-date)
                  (local-date))))

(defn being-maintained-until [_model date]
  date)

(defn get-unavailable-from [reservation]
  (if (:item_id reservation)
    (local-date)
    (t/max (local-date (:start_date reservation))
           (local-date))))

(defn get-unavailable-until [reservation model]
  (let [date (t/max (if (late? reservation)
                      (t/plus (local-date) replacement-interval)
                      (local-date (:end_date reservation)))
                    (local-date))]
    (cond->> date
      (> (:maintenance_period model) 0)
      (being-maintained-until model))))

(defn explode-date-range [start end]
  (->> (t/iterate t/plus start (t/days 1))
       (take-while #(not= % (t/plus end (t/days 1))))))

(defn most-recent-before-or-equal [changes date]
  (let [matching (->> changes
                      keys
                      (filter #(or (= % date) (t/before? % date))))]
    (when (seq matching)
      (apply t/max matching))))

(defn between [changes date1 date2]
  (let [most-recent-date (or (most-recent-before-or-equal changes date1)
                             date1)
        dates-between (set/intersection (set (keys changes))
                                        (set (explode-date-range most-recent-date date2)))]
    (select-keys changes dates-between)))

(defn insert-for-single-date [changes date]
  (if (get changes date)
    changes
    (let [allocs-source-date (most-recent-before-or-equal changes date)
          allocs (get changes allocs-source-date)]
      (assoc changes date allocs))))

(defn insert-for-time-span [changes date1 date2]
  (-> changes
      (insert-for-single-date date1)
      (insert-for-single-date (t/plus date2 (t/days 1)))))

(defn update-allocations [inner-changes allocated-group-id reservation]
  (let [group-alloc-path [s/MAP-VALS (s/submap [allocated-group-id]) s/MAP-VALS]]
    (->> inner-changes
         (s/transform (conj group-alloc-path :in-quantity) #(- % (:quantity reservation)))
         (s/transform (conj group-alloc-path :running-reservations) #(conj % (:id reservation)))
         (into {}))))

(defn update-inner-changes
  [changes date1 date2 reservation inventory-pool-and-model-group-ids]
  (let [inner-changes (between changes date1 date2)
        allocated-group-id (a/get-group-id reservation
                                           inner-changes
                                           inventory-pool-and-model-group-ids)]
    (merge changes
           (update-allocations inner-changes allocated-group-id reservation))))

(defn extend-with
  [changes reservation model inventory-pool-and-model-group-ids]
  (let [unavailable-from (get-unavailable-from reservation)
        unavailable-until (get-unavailable-until reservation model)]
    (-> changes
        (insert-for-time-span unavailable-from unavailable-until)
        (update-inner-changes unavailable-from
                              unavailable-until
                              reservation
                              inventory-pool-and-model-group-ids))))

(defn main
  ([tx model-id pool-id] (main tx model-id pool-id nil))
  ([tx model-id pool-id exclude-res-ids]
   (let [model (q/get-model-by-id tx model-id)
         running-reservations (q/running-reservations tx model-id pool-id exclude-res-ids)
         entitlements (q/get-entitlements-for-model-and-pool tx model-id pool-id)
         inventory-pool-and-model-group-ids
         (q/get-inventory-pool-and-model-group-ids tx model-id pool-id)
         initial-changes (init tx entitlements pool-id)]
     (reduce (fn [changes reservation]
               (extend-with changes reservation model inventory-pool-and-model-group-ids))
             initial-changes
             running-reservations))))
