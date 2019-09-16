(ns leihs.core.user.queries
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as clj-str]
            [clojure.tools.logging :as log]
            [leihs.core.sql :as sql]))

(defn merge-search-term-where-clause [sqlmap search-term]
  (let [term-parts (map (fn [part] (str "%" part "%"))
                        (clj-str/split search-term #"\s+"))]
    (sql/merge-where sqlmap
                     (into [:and]
                           (map
                             (fn [term-percent]
                               ["~~*"
                                (->> (sql/call :concat
                                               :users.firstname
                                               (sql/call :cast " " :varchar)
                                               :users.lastname)
                                     (sql/call :unaccent))
                                (sql/call :unaccent term-percent)])
                             term-parts)))))
