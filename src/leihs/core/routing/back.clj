(ns leihs.core.routing.back
  (:refer-clojure :exclude [str keyword])
  (:require
    [bidi.bidi :as bidi]
    [bidi.ring :refer [make-handler]]
    [clojure.tools.logging :as logging]
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.defaults :as defaults]
    [leihs.core.json :as json]
    [leihs.core.json-protocol]
    [leihs.core.sql :as sql]
    [leihs.core.url.core :as url]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    [taoensso.timbre :refer [debug info warn error]]
    ))

;;; resolving by handler ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce resolve-table* (atom nil))

(defonce paths* (atom nil))

(defn dispatch-to-handler [request]
  (if-let [handler (:handler request)]
    (handler request)
    (throw
      (ex-info
        "There is no handler for this resource and the accepted content type."
        {:status 404}))))

(defn wrap-resolve-handler
  ([handler]
   (wrap-resolve-handler handler nil))
  ([handler fallback-handler]
   (fn [request]
     (let [path (or (-> request :path-info presence)
                    (-> request :uri presence))
           paths @paths*
           {route-params :route-params
            handler-key :handler} (bidi/match-pair
                                    paths {:remainder path :route paths})
           handler-fn (-> @resolve-table*
                          (get handler-key)
                          (#(cond (map? %) (:handler %)
                                  (and (nil? %) fallback-handler) fallback-handler
                                  :else %)))
           request (assoc request :route-params route-params
                          :handler-key handler-key
                          :handler handler-fn)
           resp (or  (handler request) {})]
       (if (and (not handler-key) (-> resp :status not))
         (assoc resp :status 404)
         resp)))))


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
                 (update-in [:form-params] canonicalize-params-map)
                 (update-in [:route-params] #(-> %
                                                 (canonicalize-params-map :parse-json? false)
                                                 url/decode-keys))))))


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


;;; pagination ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-per-page-and-offset
  ([query {{per-page :per-page page :page} :query-params}]
   (let [per-page (or per-page defaults/PER-PAGE)
         page (or page 1)]
     (when-not per-page (throw (ex-info "DAMN NOT SET per-page" {})))
     (when (or (-> per-page presence not)
               (-> per-page integer? not)
               (> per-page 1000)
               (< per-page 1))
       (throw (ex-info "The query parameter per-page must be present and set to an integer between 1 and 1000."
                       {:status 422})))
     (when (or (-> page presence not)
               (-> page integer? not)
               (< page 0))
       (throw (ex-info "The query parameter page must be present and set to a positive integer."
                       {:status 422}))))
   (set-per-page-and-offset query per-page page))
  ([query per-page page]
   (-> query
       (sql/limit per-page)
       (sql/offset (* per-page (- page 1))))))


;;; default-query-params-mixin ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn mixin-default-query-params [request default-query-params]
  (-> request
      (update-in [:query-params] #(merge default-query-params %))
      (update-in [:query-params-raw] #(merge default-query-params %))))

(defn wrap-mixin-default-query-params [handler default-query-params]
  (fn [request]
    (-> request
        (mixin-default-query-params default-query-params)
        handler)))

;;; init ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init [paths resolve-table]
  (info " initalizing routing ..." )
  (reset! paths* paths)
  (reset! resolve-table* resolve-table)
  (info " initiazlied routing " {'paths @paths* 'resolve-table @resolve-table*}))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
