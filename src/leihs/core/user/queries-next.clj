(ns leihs.core.user.queries-next
  (:require 
    [honey.sql :refer [format] :rename {format sql-format}]
    [honey.sql.helpers :as sql]
    [next.jdbc :as jdbc]
    [next.jdbc.sql :refer [query] :rename {query jdbc-query}]
    [clojure.string :as clj-str]))

(defn merge-search-term-where-clause [sqlmap search-term]
  (let [term-parts (map (fn [part] (str "%" part "%"))
                        (clj-str/split search-term #"\s+"))]
    (sql/where sqlmap
               (into [:and]
                     (map
                       (fn [term-percent]
                         ["~~*"
                          [:unaccent [:concat
                                      :users.firstname
                                      [:cast " " :varchar]
                                      :users.lastname]]
                          [:unaccent term-percent]])
                       term-parts)))))

(comment (-> (sql/where ["~~*" :foo "bar"])
             sql-format))
