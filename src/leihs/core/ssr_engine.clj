(ns leihs.core.ssr-engine
  (:require
    [clojure.core.memoize :as memoize]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [leihs.core.json :refer [to-json]]
    [me.raynes.conch :refer [programs]]
    [logbug.debug :as debug])
  (:import [java.io BufferedReader InputStreamReader]))

(programs node)

(def render-wait-time-max-ms 1000)

(defn ssr-cli-input-stream []
  (-> "leihs-ssr.js"
      io/resource
      io/input-stream))

; TODO workaround because node process won't exit (sometimes?)
; TODO fix properly
(defn _render-react
  [name props]
  (try
    (let [res (with-open [in (ssr-cli-input-stream)]
                (log/debug "-" "render" name (to-json props))
                (node
                  "-" "render" name (to-json props)
                  {:in (-> in InputStreamReader. BufferedReader.)
                   :timeout render-wait-time-max-ms
                   :throw false
                   :verbose true}))
          exit-code (-> res :exit-code deref)]
      (cond
        (= exit-code :timeout) (log/warn "SSR Timeout, continuing anyways")
        (not= exit-code 0) (log/warn "SSR exit-code "exit-code", continuing anyways"))
      (:stdout res))
    (catch Exception e
      (throw (ex-info (str "Render Error! cause: " (ex-message e))
                      {:status 500} e)))))

(def render-react (memoize/lru _render-react :lru/threshold 100))

;(debug/debug-ns *ns*)
