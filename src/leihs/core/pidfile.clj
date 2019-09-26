(ns leihs.core.pidfile
  (:require [clj-pid.core :as pid]
            [clojure.tools.logging :as log]))

(defn handle [file-path]
  (.mkdirs (java.io.File. "./tmp"))
  (pid/save file-path)
  (pid/delete-on-shutdown! file-path)
  (log/info "pidfile written to:" file-path))
