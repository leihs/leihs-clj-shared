(ns leihs.core.utils
  (:refer-clojure :exclude [-> ->>]))

(defmacro ->
  "Modified version of clojure.core/-> to support not having to wrap
  the anonymous functions in additional parenthesis:
  (-> 1 #(+ % 1)) instead of (-> 1 (#(+ % 1)))"
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (let [form (first forms)
            threaded (if (and (seq? form)
                              (not (= (first form) 'fn*)))
                       (with-meta `(~(first form) ~x ~@(next form))
                                  (meta form))
                       (list form x))]
        (recur threaded (next forms)))
      x)))

(defmacro ->>
  "Modified version of clojure.core/->> to support not having to wrap
  the anonymous functions in additional parenthesis:
  (->> 1 #(+ % 1)) instead of (->> 1 (#(+ % 1)))"
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (let [form (first forms)
            threaded (if (and (seq? form)
                              (not (= (first form) 'fn*)))
                       (with-meta `(~(first form) ~@(next form) ~x)
                                  (meta form))
                       (list form x))]
        (recur threaded (next forms)))
      x)))

(comment
  (-> 1 #(+ % 1) dec))

(defn my-cast [data]
  (let [update-cast (fn [data key cast-type]
                      (if (contains? data key)
                        (assoc data key [[:cast (get data key) cast-type]])
                        data))]
    (-> data
        (update-cast :id :uuid)
        (update-cast :category_id :uuid)
        (update-cast :template_id :uuid)
        (update-cast :room_id :uuid)
        (update-cast :order_status :order_status_enum)
        (update-cast :budget_period_id :uuid)
        (update-cast :user_id :uuid)
        (update-cast :request_id :uuid)
        (update-cast :main_category_id :uuid)
        (update-cast :inspection_start_date :timestamptz)
        (update-cast :end_date :timestamptz)
        (update-cast :metadata :jsonb)
        (update-cast :meta_data :jsonb))))
