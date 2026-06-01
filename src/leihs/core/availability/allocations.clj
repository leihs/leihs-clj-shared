(ns leihs.core.availability.allocations
  (:require
   [leihs.core.core :refer [detect]]))

(defn intersection [a1 a2]
  (filter #(some #{%} a2) a1))

(defn difference [a1 a2]
  (remove #(some #{%} a2) a1))

(defn get-max-possible-quantities-for-group-ids-and-changes
  "Returns a map: {group-id quantity, ...}"
  [group-ids inner-changes]
  (reduce (fn [memo g-id]
            (let [possible-quantity (->> inner-changes
                                         vals
                                         (map #(-> % (get g-id) :in-quantity (or 0)))
                                         (apply min))]
              (assoc memo g-id possible-quantity)))
          {}
          group-ids))

(defn get-group-id [reservation inner-changes pool-and-model-group-ids]
  (let [user-group-ids (intersection (:user_group_ids reservation)
                                     pool-and-model-group-ids)
        not-user-group-ids (difference pool-and-model-group-ids user-group-ids)
        group-ids-to-check (concat user-group-ids [:general] not-user-group-ids)
        max-possible-quantities (get-max-possible-quantities-for-group-ids-and-changes
                                  group-ids-to-check inner-changes)]
    (or (detect #(-> max-possible-quantities
                     (get %)
                     (or 0)
                     (>= (:quantity reservation)))
                group-ids-to-check)
        :general)))
