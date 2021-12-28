(ns leihs.core.logging
  (:require
    [taoensso.timbre :as timbre :refer [debug info]]))

(def LOGGING_CONFIG
  {:min-level [[#{
                  ; "leihs.admin.resources.inventory-pools.*"
                  ; "leihs.core.auth.core.*"
                  } :debug]
               [#{
                  #?(:clj "com.zaxxer.hikari.*")
                  "leihs.*"} :info]
               [#{"*"} :warn]]
   :log-level nil})


(defn init
  ([] (init LOGGING_CONFIG))
  ([logging-config]
   (info "initializing logging " logging-config)
   (timbre/merge-config! logging-config)
   (info "initialized logging " (pr-str timbre/*config*))))
