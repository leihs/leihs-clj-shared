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
