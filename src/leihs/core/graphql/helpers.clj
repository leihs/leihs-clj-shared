(ns leihs.core.graphql.helpers
  (:require
    [camel-snake-kebab.core :as csk]
    [cheshire.core :refer [generate-string] :rename {generate-string to-json}]
    [clj-time.core :as clj-time]
    [clojure.tools.logging :as log]
    [clojure.string :as string]
    [taoensso.timbre :as timbre :refer [debug info spy warn]]
    [com.walmartlabs.lacinia [executor :as executor]]
    [com.walmartlabs.lacinia.resolve :refer [resolve-as wrap-resolver-result with-extensions]]
    [leihs.core.ring-exception :refer [get-cause]]
    [wharf.core :refer [transform-keys]]
    ))

(defn error-as-graphql-object
  [code message]
  {:errors [{:message (str message), ; if message is nil convert to ""
             :extensions {:code code,
                          :timestamp (-> (clj-time/now)
                                         .toString)}}],
   :data []})

(defn error-as-graphql
  [code message]
  (to-json (error-as-graphql-object code message)))

(defn wrap-resolver-with-error
  "Try resolver, catch exception and transform it into a graphql error."
  [resolver]
  (fn [context args value]
    (try (resolver context args value)
         (catch Throwable e*
           (let [e (get-cause e*)
                 m (.getMessage e)
                 n (-> e*
                       .getClass
                       .getSimpleName)
                 status-code (:status (ex-data e*))]
             (warn (or m n))
             (debug e)
             (resolve-as nil
                         {:message (str m),
                          :code status-code
                          :exception n}))))))

(defn wrap-resolver-with-camelCase
  "Change case type for the keys in the result map."
  [resolver]
  (fn [context args value]
    (transform-keys csk/->camelCase
                    (resolver context args value))))

(defn wrap-resolver-with-kebab-case
  "Change case type for the keys in the args and value map."
  [resolver]
  (fn [context args value]
    (resolver context
              (transform-keys csk/->kebab-case args)
              (transform-keys csk/->kebab-case value))))

(defn find-all-nested
  [m k]
  (->> (tree-seq map? vals m)
       (filter map?)
       (keep k)))

(defn attach-overall-timing [result]
  (let [all-timings (find-all-nested result :execution/timings)
        all-elapsed (->> all-timings flatten (map :elapsed))]
    (assoc-in result
              [:extensions :overall-timing :elapsed]
              (apply + all-elapsed))))

(defn transform-resolvers [m f]
  (into {} (for [[k v] m] [k (f v)])))

;#### debug ###################################################################
; (debug/debug-ns 'cider-ci.utils.shutdown)
; (debug/debug-ns *ns*)
; (debug/undebug-ns *ns*)
