(ns leihs.core.shutdown
  (:refer-clojure :exclude [str keyword])
  (:require
   [clj-pid.core :as pid]
   [clj-yaml.core :as yaml]
   [clojure.java.io :as io]
   [leihs.core.core :refer [keyword str presence]]
   [logbug.debug :as debug]
   [signal.handler]
   [taoensso.timbre :refer [debug error info spy warn]]))

(def pid-file-option
  [nil "--pid-file PIDFILE"
   :default nil ; "./tmp/service.pid"
   :parse-fn yaml/parse-string])

(defn pid [options]
  (info "PID" (pid/current))
  (when-let [pid-file (:pid-file options)]
    (info "PID-FILE" pid-file)
    (io/make-parents pid-file) ; ensure dirs exist before creating file!
    (pid/save pid-file)
    (pid/delete-on-shutdown! pid-file)))

; nREPL >= 1.3.0 runs eval threads as daemon threads (nrepl.util.threading/DaemonThreadFactory).
; A reload-triggered server restart briefly has zero non-daemon threads (old http-kit server
; stopped, new one not started yet), which lets the JVM exit mid-restart. This thread keeps at
; least one non-daemon thread alive at all times so that gap is never fatal.
(defonce keep-alive-thread* (atom nil))

(defn keep-jvm-alive! []
  (when-not @keep-alive-thread*
    (reset! keep-alive-thread*
            (doto (Thread. ^Runnable (fn [] (Thread/sleep Long/MAX_VALUE))
                           "leihs-jvm-keep-alive")
              (.setDaemon false)
              .start))))

(defn run-return-fn
  "For use as catcher/snatch's :return-fn around an app's run function.
   In dev-mode, logs and returns instead of exiting, so a failure during
   a reload-triggered restart doesn't kill the whole dev JVM."
  [options]
  (fn [e]
    (if (:dev-mode options)
      (error e "run failed (dev-mode, not exiting)")
      (System/exit -1))))

(defn init [options]
  (when (:dev-mode options) (keep-jvm-alive!))
  (pid options)
  (info "Registering SIGTERM handler for shutdown.")
  (signal.handler/with-handler :term
    (System/exit 0)))
