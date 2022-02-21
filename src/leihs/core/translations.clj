(ns leihs.core.translations
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.string :as string]
    [clojure.tools.logging :as log]
    [compojure.core :as cpj]
    [leihs.core.paths :refer [path]]
    [leihs.core.sql :as sql]
    ))

(defn transform [ts]
  (reduce (fn [m {k :key t :translation l :language_locale}]
            (let [ks (-> k
                         (string/split #"\.")
                         (->> (map keyword) (into [])))
                  ks-with-locale (conj ks (keyword l))]
              (assoc-in m ks-with-locale t)))
          {}
          ts))

(defn fetch-from-db
  ([tx prefix] (fetch-from-db tx prefix nil))
  ([tx prefix user-id]
   (let [translations (-> (sql/select :*)
                          (sql/from (sql/call :get_translations user-id))
                          (sql/where ["~~*" :key (str prefix ".%")])
                          sql/format
                          (->> (jdbc/query tx))
                          transform)
         languages (-> (sql/select :*)
                       (sql/from :languages)
                       (sql/where [:= :active true])
                       sql/format
                       (->> (jdbc/query tx)))]
     {:translations translations
      :languages languages})))

(defn translations-handler
  [{tx :tx user :authenticated-entity {:keys [prefix]} :params}]
  {:body (fetch-from-db tx prefix (:id user))})

(def routes
  (cpj/routes
    (cpj/GET (path :translations) [] #'translations-handler)))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
