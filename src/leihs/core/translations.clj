(ns leihs.core.translations
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [compojure.core :as cpj]
            [leihs.core.paths :refer [path]]
            [leihs.core.sql :as sql]))

(defn fetch-from-db
  ([tx prefix] (fetch-from-db tx prefix nil))
  ([tx prefix user-id]
   (-> (sql/select :*)
       (sql/from (sql/call :get_translations user-id))
       (sql/where ["~~*" :key (str prefix ".%")])
       sql/format
       (->> (jdbc/query tx))
       #_(->> (group-by :language_locale)))))

(defn transform [ts]
  (reduce (fn [m {k :key t :translation l :language_locale}]
            (let [ks (-> k
                         (string/split #"\.")
                         (->> (map keyword) (into [])))
                  ks-with-locale (conj ks (keyword l))]
              (assoc-in m ks-with-locale t)))
          {}
          ts))

(defn translations-handler
  [{tx :tx user :authenticated-entity {:keys [prefix]} :params}]
  {:body (-> (fetch-from-db tx prefix (:id user))
             transform)})

(def routes
  (cpj/routes
    (cpj/GET (path :translations) [] #'translations-handler)))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
