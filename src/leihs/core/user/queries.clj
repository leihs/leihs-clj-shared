(ns leihs.core.user.queries
  (:require
   [clojure.string :as clj-str]
   [honey.sql.helpers :as sql]))

(defn merge-search-term-where-clause [sqlmap search-term]
  (let [term-parts (map (fn [part] (str "%" part "%"))
                        (clj-str/split search-term #"\s+"))]
    (sql/where sqlmap
               (into [:and]
                     (map
                      (fn [term-percent]
                        [(keyword "~~*")
                         (->> [:unaccent [:concat
                                          :users.firstname
                                          [:cast " " :varchar]
                                          :users.lastname]])
                         [:unaccent term-percent]])
                      term-parts)))))
