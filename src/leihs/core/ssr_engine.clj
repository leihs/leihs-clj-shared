(ns leihs.core.ssr-engine
  (:require
   [clojure.core.memoize :as memoize]
   [clojure.java.io :as io]
   [leihs.core.json :refer [to-json]]
   [logbug.debug :as debug]
   [me.raynes.conch :refer [programs]]
   [taoensso.timbre :refer [debug info warn error spy]])
  (:import
   [java.io BufferedReader InputStreamReader]))

(defonce dev-mode?* (atom nil))

(programs node)

(defn ssr-cli-input-stream []
  (-> "leihs-ssr.js"
      io/resource
      io/input-stream))

(defn- _render-react
  [name props]
  (try
    (let [res (with-open [in (ssr-cli-input-stream)]
                (debug "-" "render" name (to-json props))
                (node "-" "render" name (to-json props)
                      {:in (-> in InputStreamReader. BufferedReader.)
                       :timeout (if @dev-mode?* 1000 5000)
                       :throw false
                       :verbose true}))
          exit-code (-> res :exit-code deref)]
      (cond
        (= exit-code 0) (debug "SSR OK")
        (and (not @dev-mode?*)
             (not= exit-code 0)) (throw (ex-info "SSR Error"
                                                 (dissoc res :in :stdout :proc)))
        (and @dev-mode?*
             (not= exit-code 0)) (warn "SSR exit-code " exit-code
                                       ", continuing anyways "
                                       (dissoc res :in :stdout :proc)))
      (:stdout res))
    (catch Exception e
      (throw (ex-info (str "SSR-Render Error! cause: " (ex-message e))
                      {:status 500} e)))))

(def ^:private _render-react_cached (memoize/lru _render-react :lru/threshold 100))

(defn render-react [& args]
  (when-not (boolean? @dev-mode?*)
    (throw (ex-info "leihs.core.ssr-engine is not initialized" {})))
  (if @dev-mode?*
    (apply _render-react args)
    (apply _render-react_cached args)))

(defn init [options]
  (info "initializing SSR-Engine")
  (reset! dev-mode?* (:dev-mode options))
  (when @dev-mode?*
    (warn "running in dev-mode, should not be used in production"))
  (info "initialized SSR-Engine: " {:dev-mode @dev-mode?*}))

;(debug/debug-ns *ns*)
