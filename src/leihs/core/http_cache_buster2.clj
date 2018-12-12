(ns leihs.core.http-cache-buster2
  (:require
    [pandect.algo.sha1 :as sha1]
    [ring.middleware.resource :as resource]
    [ring.util.codec :as codec]
    [ring.util.request :as request]
    [ring.util.response :as response]
    [clojure.core.memoize :as memoize]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))


;##############################################################################

(def resource-cache* (atom {}))

(defn cachable-resource-response [path request root-path options]
  (some-> request
          (assoc :path-info (codec/url-encode path))
          (resource/resource-request root-path options)
          (update-in [:headers] dissoc "Last-Modified")
          (assoc-in [:headers "Cache-Control"] "public, max-age=31536000")
          (update-in [:body] slurp)))

(defn never-expire-cached-resource! [path request root-path options]
  (or (get @resource-cache* path)
      (let [response (cachable-resource-response path request root-path options)]
        (swap! resource-cache* assoc path response)
        response)))
        

;##############################################################################

(defonce cache-bust-path->original-path* (atom {}))
(defonce original-path->cache-bust-path* (atom {}))

(defn extension [path]
  (->> path
       (re-matches #".*\.([^\.]+)")
       last))

(defn cache-bust-path! [path]
  (let [signature (-> path sha1/sha1)
        extension (extension path)
        cache-bust-path (str path "_" signature "." extension)]
    (swap! cache-bust-path->original-path* assoc cache-bust-path path)
    (swap! original-path->cache-bust-path* assoc path cache-bust-path)
    cache-bust-path))

(defn cache-busted-path [path]
  (or (get @original-path->cache-bust-path* path)
      path))

(defn cache-bust [path request root-path options]
  (if-let [original-path (get @cache-bust-path->original-path* path)]
    (never-expire-cached-resource! original-path request root-path options)
    (ring.util.response/redirect
      (str (:context request) (cache-bust-path! path)))))


;##############################################################################

(defn path-matches? [path xp]
  (boolean
    (some (fn [p]
            (if (string? p)
              (= p path)
              (re-find p path)))
          xp)))

(defn resource [request root-path options]
  (let [path (-> request request/path-info codec/url-decode)]
    (cond 
      (-> options :cache-enabled? not) (resource/resource-request 
                                         request root-path options)
      (or (path-matches? path (:cache-bust-paths options))
          (get @cache-bust-path->original-path* path)
          ) (cache-bust path request root-path options)
      (path-matches?
        path (:never-expire-paths options)) (never-expire-cached-resource!
                                              path request root-path options)
      :else (resource/resource-request request root-path options))))


;##############################################################################

(def default-options
  {:cache-bust-paths []
   :never-expire-paths []
   :cache-enabled? true})

(defn wrap-resource
  "Replacement for ring.middleware.resource/wrap-resource.

  Accepts the following additional options:

  :cache-enabled? - pass directly on to resource/resource-request if set to false, 
  regardless of the value of :cache-bust-paths or :never-expire-paths, 
  default is true

  :cache-bust-paths - collection, each value is either a string or a regex,
  resources with matching paths will be cache-busted and a redirect
  response to the cache-busted path is send; subsequent calls to
  cache-busted-path will return the cache-busted path.

  :never-expire-paths - collection, each value is either a string or a regex,
  resources with matching paths will be set to never expire 

  Note: the body of the resource will be cached in memory if either of
  the pathes match and :cache-enabled? is true. Ensure there will be 
  enough heap space.  
  "

  ([handler root-path]
   (wrap-resource handler root-path default-options))
  ([handler root-path options]
   (fn [request]
     (or (resource request root-path (merge default-options options))
         (handler request)))))



;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
