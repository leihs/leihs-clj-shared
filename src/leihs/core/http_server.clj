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
(def http-threads-key :http-threads)
(def options-keys [http-host-key http-port-key http-threads-key])

(defn cli-options
  [& {:keys [default-http-port] :or {default-http-port 3200}}]
  [[nil (long-opt-for-key http-host-key) "HTTP Host, falls back to 'localhost'"
    :default (or (some-> http-host-key env presence )
                 "localhost")]
   [nil (long-opt-for-key http-port-key) "HTTP Port, falls back to 3200"
    :default (or (some-> http-port-key env presence Integer/parseInt)
                 default-http-port)
    :parse-fn #(Integer/parseInt %)]
   [nil (long-opt-for-key http-threads-key)
    :default (or (some-> http-threads-key env Integer/parseInt)
                 (-> NCPUS (/ 4) Math/ceil int))
    :parse-fn #(Integer/parseInt %)
    :validate [#(<= 1 % NCPUS) "Must be an integer <= num cpus"]]])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn stop []
  (when-let [stop-server @stop-server*]
    (info "stopping running http-server" )
    (stop-server :timeout 100)
    (Thread/sleep 200)
    (reset! stop-server* nil)
    (info "stopped http-server")))

(defn start [options main-handler]
  "Starts (or stops and then starts) the webserver"
  (when-not @stop-server* ; only-once, not on code reload
    (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (stop)))))
  (when @stop-server* (stop))
  (let [server-conf (-> options
                        (select-keys options-keys)
                        (merge {:worker-name-prefix "http-server-worker-"})
                        (rename-keys {:http-port :port
                                      :http-host :ip
                                      :http-threads :thread}))]
    (info "starting http-server " server-conf)
    (reset! stop-server* (http-kit/run-server main-handler server-conf))
    (info "started http-server ")))
