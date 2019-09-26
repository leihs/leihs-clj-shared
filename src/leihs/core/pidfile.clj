(ns leihs.core.pidfile
  (:require [clj-pid.core :as pid]))

(defn handle
  []
  (let [pid-file "./tmp/server_pid"]
    (.mkdirs (java.io.File. "./tmp"))
    (pid/save pid-file)
    (pid/delete-on-shutdown! pid-file)))
