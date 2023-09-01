(ns leihs.core.shutdown
  (:refer-clojure :exclude [str keyword])
  (:require
    [clj-pid.core :as pid]
    [clj-yaml.core :as yaml]
    [clojure.java.io :as io]
    [leihs.core.core :refer [keyword str presence]]
    [logbug.debug :as debug]
    [signal.handler]
    [taoensso.timbre :refer [debug error info spy warn]]
    ))

(def pid-file-option
  [nil "--pid-file PIDFILE"
   :default nil ; "./tmp/service.pid"
   :parse-fn yaml/parse-string
   ])

(defn pid [options]
  (info "PID" (pid/current))
  (when-let [pid-file (:pid-file options)]
    (info "PID-FILE" pid-file)
    (io/make-parents pid-file) ; ensure dirs exist before creating file!
    (pid/save pid-file)
    (pid/delete-on-shutdown! pid-file)))

(defn init [options]
  (pid options)
  (info "Registering SIGTERM handler for shutdown.")
  (signal.handler/with-handler :term
    (System/exit 0)))
