(ns leihs.core.resources.categories.filter)

(defn current-or-any-descendent? [pred category]
  (or (pred category)
      (some (partial current-or-any-descendent? pred)
            (:children category))))

(defn deep-filter [pred tree]
  (->> tree
       (filter (partial current-or-any-descendent? pred))
       (map #(update % :children (partial deep-filter pred)))))

(comment
  (require '[clojure.test :as t])

  (let [tree [{:name "foo"
               :children [{:name "bar"
                           :children [{:name "baz"
                                       :children []}]}]}
              {:name "qux"
               :children [{:name "quux"
                           :children [{:name "foo"
                                       :children []}]}]}]
        term "oo"
        result (deep-filter #(re-matches (re-pattern (str "(?i).*" term ".*"))
                                         (:name %))
                            tree)]
    ; result
    (t/is (= result [{:name "foo",
                      :children []}
                     {:name "qux",
                      :children [{:name "quux",
                                  :children [{:name "foo",
                                              :children []}]}]}]))))
