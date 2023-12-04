; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns leihs.core.http-server
  (:refer-clojure :exclude [str keyword])
  (:require
   [clojure.set :refer [rename-keys]]
   [clojure.walk :refer [keywordize-keys]]
   [cuerdas.core :as string :refer [snake kebab upper human]]
   [environ.core :refer [env]]
   [leihs.core.core :refer [keyword str presence]]
   [org.httpkit.server :as http-kit]
   [taoensso.timbre :refer [debug info warn error spy]]))

(defonce stop-server* (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn long-opt-for-key [k]
  (str "--" (kebab k) " " (-> k snake upper)))

(def NCPUS (.availableProcessors (Runtime/getRuntime)))

(def http-host-key :http-host)
(def http-port-key :http-port)
(def http-worker-prefix :http-worker-prefix)
(def http-min-threads-key :http-min-threads)
(def http-max-threads-key :http-max-threads)
(def http-thread-keep-alive-seconds :http-thread-keep-alive-seconds)
(def http-queue-capacity :http-queue-capacity)

(defn cli-options
  [& {:keys [default-http-port] :or {default-http-port 3200}}]
  [[nil (long-opt-for-key http-host-key) "HTTP Host, falls back to 'localhost'"
    :default (or (some-> http-host-key env presence)
                 "localhost")]
   [nil (long-opt-for-key http-port-key) "HTTP Port, falls back to 3200"
    :default (or (some-> http-port-key env presence Integer/parseInt)
                 default-http-port)
    :parse-fn #(Integer/parseInt %)]
   [nil (long-opt-for-key http-worker-prefix)
    :default "leihs-service-http-worker-"]
   [nil (long-opt-for-key http-max-threads-key)
    :default (or (some-> http-max-threads-key env Integer/parseInt)
                 (-> NCPUS (* 3) (/ 4) Math/ceil int))
    :parse-fn #(Integer/parseInt %)
    :validate [#(and (int? %)
                     (<= 1 %)) "Must be an integer >= 1"]]
   [nil (long-opt-for-key http-min-threads-key)
    :default (or (some-> http-min-threads-key env Integer/parseInt)
                 1)
    :parse-fn #(Integer/parseInt %)
    :validate [#(and (int? %)
                     (<= 1 %)) "Must be an integer >= 1"]]
   [nil (long-opt-for-key http-thread-keep-alive-seconds)
    :default (or (some-> http-thread-keep-alive-seconds env Long/parseLong)
                 10)
    :parse-fn #(Long/parseLong % 10)]
   [nil (long-opt-for-key http-queue-capacity)
    :default (or (some-> http-queue-capacity env Integer/parseInt)
                 (-> NCPUS (* 64) int))]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn connection-pool [opts]
  (let [ptf   (org.httpkit.PrefixThreadFactory. (http-worker-prefix opts))
        queue (java.util.concurrent.ArrayBlockingQueue. (http-queue-capacity opts))
        pool  (java.util.concurrent.ThreadPoolExecutor.
               (long (http-min-threads-key opts)) (long (http-max-threads-key opts))
               (http-thread-keep-alive-seconds opts) java.util.concurrent.TimeUnit/SECONDS
               queue ptf)]
    {:queue queue
     :pool  pool}))

(defn stop []
  (when-let [stop-server @stop-server*]
    (info "stopping running http-server")
    (stop-server :timeout 100)
    (Thread/sleep 200)
    (reset! stop-server* nil)
    (info "stopped http-server")))

(defn start [options main-handler]
  "Starts (or stops and then starts) the webserver"
  (when-not @stop-server* ; only-once, not on code reload
    (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (stop)))))
  (when @stop-server* (stop))
  (let [pool (connection-pool options)
        server-conf (-> options
                        (select-keys [:http-port :http-host])
                        (rename-keys {:http-port :port
                                      :http-host :ip})
                        (assoc :pool pool))]
    (info "starting http-server " server-conf)
    (reset! stop-server*
            (http-kit/run-server main-handler server-conf))
    (info "started http-server ")))
