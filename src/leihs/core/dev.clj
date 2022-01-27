(ns leihs.core.dev
  (:require
    [leihs.core.repl]
    [leihs.core.http-server]
    [leihs.core.db]
    [clojure.tools.namespace.repl :as ctnr]
    [taoensso.timbre :refer [debug info warn error]]
    ))

;(ctnr/disable-unload!)
;(ctnr/disable-reload!)

(defonce enabled?* (atom false))

(defonce main-args* (atom nil))

(defonce main* (atom nil))

(defn init [main-args main src-dirs]
  (when-not @enabled?*
    (info 'init [main main-args src-dirs])
    (apply ctnr/set-refresh-dirs src-dirs)
    (doseq [nss ['leihs.core.dev
                 'leihs.core.repl
                 ;'leihs.core.db
                 ;'leihs.core.routing.back
                 'leihs.core.http-server]]
      (info "disabling unload/reload for " nss)
      (ctnr/disable-reload! (create-ns nss)))
    (reset! enabled?* true)
    (reset! main-args* (or main-args []))
    (reset! main* main)))

(defn reload! []
  (if-not @enabled?*
    (warn "reload not enabled, skipping reload!")
    (do (info "reload! invoked")
        (leihs.core.http-server/stop)
        (Thread/sleep 1000)
        (leihs.core.db/close)
        (Thread/sleep 1000)
        (ctnr/refresh)
        (@main* @main-args*))))

;;; DEV ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; start with ["--dev-mode" "true"] and invoke (reload!) to reload and restart
