(ns leihs.core.logging
  (:require
    #?(:clj [taoensso.timbre.tools.logging])
    [taoensso.timbre :as timbre :refer [debug info]]))

(def LOGGING_CONFIG
  {:min-level [[#{
                  ; examples:
                  ; "leihs.core.auth.core.*"
                  ; "leihs.admin.resources.inventory-pools.*"
                  ; "leihs.admin.routes"
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
   #?(:clj (taoensso.timbre.tools.logging/use-timbre))
   (info "initialized logging " (pr-str timbre/*config*))))
