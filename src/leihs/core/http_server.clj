; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns leihs.core.http-server
  (:refer-clojure :exclude [str keyword])
  (:require
    [aleph.http :as http-server]
    [clojure.set :refer [rename-keys]]
    [cuerdas.core :as string :refer [snake kebab upper human]]
    [environ.core :refer [env]]
    [leihs.core.core :refer [keyword str presence]]
    [taoensso.timbre :refer [debug info warn error spy]]))


(defonce server* (atom nil))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn long-opt-for-key [k]
  (str "--" (kebab k) " " (-> k snake upper)))

(def http-host-key :http-host)
(def http-port-key :http-port)
(def options-keys [http-host-key http-port-key])

(defn cli-options
  [& {:keys [default-http-port] :or {default-http-port 3200}}]
  [[nil (long-opt-for-key http-host-key) "HTTP Host, falls back to 'localhost'"
    :default (or (some-> http-host-key env presence )
                 "localhost")]
   [nil (long-opt-for-key http-port-key) "HTTP Port, falls back to 3200"
    :default (or (some-> http-port-key env presence Integer/parseInt)
                 default-http-port)
    :parse-fn #(Integer/parseInt %)]])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn stop []
  (when-let [server @server*]
    (info "stopping running http-server: " server)
    (.close server)
    (reset! server* nil)
    (info "stopped http-server")))

(defn start [options main-handler]
  "Starts (or stops and then starts) the webserver"
  (when-not @server* ; only-once, not on code reload
    (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (stop)))))
  (when @server* (stop))
  (let [options (select-keys options options-keys)]
    (let [server-conf (conj {:ssl? false
                             :join? false}
                            (rename-keys options {:http-port :port
                                                  :http-host :host}))]
      (info "starting http-server " server-conf)
      (reset! server* (http-server/start-server main-handler server-conf))
      (info "started http-server " @server*))))
