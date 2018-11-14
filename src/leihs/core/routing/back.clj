(ns leihs.core.routing.back
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.json :as json]
    [leihs.core.json-protocol]

    [bidi.bidi :as bidi]
    [bidi.ring :refer [make-handler]]
    ))

;;; resolving by handler ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def resolve-table* (atom nil))

(def paths* (atom nil))

(defn dispatch-to-handler [request]
  (if-let [handler (:handler request)]
    (handler request)
    (throw
      (ex-info
        "There is no handler for this resource and the accepted content type."
        {:status 404}))))

(defn wrap-resolve-handler
  ([handler]
   (fn [request]
     (wrap-resolve-handler handler request)))
  ([handler request]
   (let [path (or (-> request :path-info presence)
                  (-> request :uri presence))
         paths @paths*
         {route-params :route-params
          handler-key :handler} (bidi/match-pair 
                                  paths {:remainder path :route paths})
         handler-fn (get @resolve-table* handler-key nil)]
     (handler (assoc request
                     :route-params route-params
                     :handler-key handler-key
                     :handler handler-fn)))))


;;; canonicalize request map ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- canonicalize-params-map 
  [params & {:keys [parse-json?]
             :or {parse-json? true}}]
  (if-not (map? params)
    params
    (->> params
         (map (fn [[k v]]
                [(keyword k)
                 (if parse-json? (json/try-parse-json v) v)]))
         (into {}))))

(defn wrap-canonicalize-params-maps [handler]
  (fn [request]
    (handler (-> request
                 (assoc :params-raw
                        (-> request :params
                            (canonicalize-params-map :parse-json? false)))
                 (assoc :query-params-raw
                        (-> request :query-params
                            (canonicalize-params-map :parse-json? false)))
                 (assoc :form-params-raw
                        (-> request :form-params
                            (canonicalize-params-map :parse-json? false)))
                 (update-in [:params] canonicalize-params-map)
                 (update-in [:query-params] canonicalize-params-map)
                 (update-in [:form-params] canonicalize-params-map)))))


;;; misc helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn wrap-add-vary-header [handler]
  "should be used if content varies based on `Accept` header, e.g. if using `ring.middleware.accept`"
  (fn [request]
    (let [response (handler request)]
      (assoc-in response [:headers "Vary"] "Accept"))))

(defn wrap-empty [handler]
  (fn [request]
    (or (handler request)
        {:status 404})))

(defn init [paths resolve-table]
  (reset! paths* paths)
  (reset! resolve-table* resolve-table))

