(ns leihs.core.graphql
  (:require [com.walmartlabs.lacinia.parser :as lacinia-parser]
            [alumbra.parser :as graphql-parser]
            [leihs.core.graphql.helpers :as helpers]
            [leihs.core.ring-exception :refer [get-cause]]
            [taoensso.timbre :refer [debug info warn error spy]]))

(def schema* (atom nil))
(def audit-exceptions* (atom nil))

(defn schema []
  (or @schema* (throw (ex-info  "Schema not initialized " {}))))

(defn init-schema! [schema]
  (reset! schema* schema)
  (info "Initialized graphQL schema."))

(defn init-audit-exceptions! [exceptions]
  (reset! audit-exceptions* exceptions))

(defn parse-query-with-exception-handling
  [schema query]
  (try (lacinia-parser/parse-query schema query)
       (catch Throwable e*
         (let [e (get-cause e*)
               m (.getMessage e*)
               n (-> e*
                     .getClass
                     .getSimpleName)]
           (warn (or m n))
           (debug e)
           (helpers/error-as-graphql-object "API_ERROR" m)))))

(defn get-query-str [request] (-> request :body :query))

(defn query-operations [query-str]
  (some->> query-str
           (parse-query-with-exception-handling (schema))
           lacinia-parser/operations))

(defn query-type? [query-str]
  (->> query-str
       query-operations
       :type
       (= :query)))

(defn mutation-type? [query-str]
  (->> query-str
       query-operations
       :type
       (= :mutation)))

(defn operation-name [query-str]
  (-> query-str
      graphql-parser/parse-document
      :alumbra/operations
      first
      :alumbra/operation-name))

(defn query? [request]
  (->> request get-query-str query-type?))

(defn mutation? [request]
  (and (-> request :request-method (= :post))
       (->> request get-query-str mutation-type?)))

(defn mutation-to-be-audited? [request]
  (let [query-str (get-query-str request)]
    (and (mutation-type? query-str)
         (not (contains? @audit-exceptions* (operation-name query-str))))))

(defn get-schema? [request]
  (->> request
       get-query-str
       query-operations
       :operations
       (= #{:__schema})))

(defn wrap-with-schema [handler]
  (fn [request]
    (handler (assoc request :graphql-schema (schema)))))
