(ns leihs.core.graphql.helpers
  (:require [clojure.tools.logging :as log]
            [cheshire.core :refer [generate-string] :rename {generate-string to-json}]
            [clj-time.core :as clj-time]
            [clojure.string :as string]
            [com.walmartlabs.lacinia [executor :as executor]]
            [com.walmartlabs.lacinia.resolve :refer [resolve-as
                                                     wrap-resolver-result
                                                     with-extensions]]
            [camel-snake-kebab.core :as csk]
            [wharf.core :refer [transform-keys]]
            [leihs.core.ring-exception :refer [get-cause]]))

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
                       .getSimpleName)]
             (log/warn (or m n))
             (log/debug e)
             (resolve-as nil
                         {:message (str m),
                          ; if message nil
                          ; convert to ""
                          :exception n}))))))

(defn wrap-resolver-with-camelCase [resolver]
  "Change case type for the keys in the result map."
  (wrap-resolver-result resolver
                        (fn [context args value value]
                          (transform-keys csk/->camelCase value))))

(defn wrap-resolver-with-kebab-case [resolver]
  "Change case type for the keys in the args map."
  (fn [context args value]
    (resolver context
              (transform-keys csk/->kebab-case args)
              value)))

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
; (logging-config/set-logger! :level :debug)
; (logging-config/set-logger! :level :info)
; (debug/debug-ns 'cider-ci.utils.shutdown)
; (debug/debug-ns *ns*)
; (debug/undebug-ns *ns*)
