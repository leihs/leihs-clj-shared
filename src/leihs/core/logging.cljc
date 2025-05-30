(ns leihs.core.logging
  (:require
   [taoensso.timbre :as timbre :refer [debug info]]))

(def LOGGING_CONFIG
  {:min-level [[#{; examples:
                  ; "leihs.core.auth.core.*"
                  ; "leihs.core.db"
                  ; "leihs.core.graphql*"
                  ; "leihs.admin.resources.inventory-pools.*"
                  ; "leihs.admin.routes"
                  ; "leihs.borrow.graphql*"
                  ; "leihs.inventory.server.resources.models.form.model.common"
                  ; "leihs.inventory.server.utils.pagination"
                  ; "leihs.inventory.server.resources.models.main"
                  "leihs.inventory.server.resources.models.form.model.common"
                  ; "leihs.borrow.resources.orders"
                  }:debug]
               [#{#?(:clj "com.zaxxer.hikari.*")
                  "leihs.*"} :info]
               [#{"*"} :warn]]
   :log-level nil})

(defn init
  ([] (init LOGGING_CONFIG))
  ([logging-config]
   (info "initializing logging " logging-config)
   (timbre/merge-config! logging-config)
   (info "initialized logging " (pr-str timbre/*config*))))
