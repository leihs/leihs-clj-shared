(ns leihs.core.shutdown
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.ds :as ds]

    [clj-pid.core :as pid]
    [signal.handler]
    [yaml.core :as yaml]
    [clojure.java.io :as io]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]))


(def pid-file-option
  [nil "--pid-file PIDFILE"
   :default "./tmp/service.pid"
   :parse-fn yaml/parse-string
   ])

(defn pid [options]
  (logging/info "PID" (pid/current))
  (when-let [pid-file (:pid-file options)]
    (logging/info "PID-FILE" pid-file)
    (io/make-parents pid-file) ; ensure dirs exist before creating file!
    (pid/save pid-file)
    (pid/delete-on-shutdown! pid-file)))


(defn init [options]
  (pid options)

  (logging/info "Registering SIGTERM handler for shutdown.")
  (signal.handler/with-handler :term
    (ds/close)
    (System/exit 0))
  )
