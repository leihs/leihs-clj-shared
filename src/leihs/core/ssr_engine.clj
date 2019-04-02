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

(def render-wait-time-max-ms 5000)

(defn ssr-cli-input-stream []
  (-> "leihs-ssr.js"
      io/resource
      io/input-stream))

(defn _render-react
  [name props]
  (try
    (with-open [in (ssr-cli-input-stream)]
      (node
        "-" "render" name (to-json props)
        {:in (-> in InputStreamReader. BufferedReader.)
         :timeout render-wait-time-max-ms}))
    (catch Exception e
      (throw (ex-info "Render Error!" {:status 500, :causes {:err e}})))))

(def render-react (memoize/lru _render-react :lru/threshold 1000))

;(debug/debug-ns *ns*)
