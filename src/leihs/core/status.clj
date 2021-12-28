(ns leihs.core.status
  (:require
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [leihs.core.db :as db]
    [logbug.debug :as debug]
    [taoensso.timbre :refer [debug info warn error]])
  (:import
    [com.codahale.metrics.health HealthCheckRegistry]
    [humanize Humanize]))

;;; health checks ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce health-check-registry* (atom nil))

(defn HealthCheckResult->m
  [r]
  {:healthy? (.isHealthy r), :message (.getMessage r), :error (.getError r)})
;(.getNames @health-check-registry*)
;(.runHealthChecks @health-check-registry*)

(defn health-checks
  []
  (some->> @health-check-registry*
           .runHealthChecks
           (map (fn [[n r]] [n
                             (-> r
                                 HealthCheckResult->m)]))
           (into {})))


;;; memory ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn check-memory-usage
  []
  ;(System/gc)
  (let [rt (Runtime/getRuntime)
        max-mem (.maxMemory rt)
        allocated-mem (.totalMemory rt)
        free (.freeMemory rt)
        used (- allocated-mem free)
        usage (double (/ used max-mem))
        ok? (and (< usage 0.95) (> free))
        stats {:ok? ok?,
               :max (Humanize/binaryPrefix max-mem),
               :allocated (Humanize/binaryPrefix allocated-mem),
               :used (Humanize/binaryPrefix used),
               :usage usage}]
    (when-not ok? (logging/fatal stats))
    stats))


;;; main ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn status-handler
  [request]
  (let [memory-status (check-memory-usage)
        health-checks (health-checks)
        body {:memory memory-status,
              :db-pool (db/status),
              :health-checks health-checks}]
    (debug body)
    {:status (if (and (->> [memory-status]
                           (map :ok?)
                           (every? true?))
                      (->> health-checks
                           (map second)
                           (map :healthy?)
                           (apply true?)))
               200
               900),
     :body body}))

(defn wrap [default-handler path]
  (fn [request]
    (debug {'request request})
    (if (= (:uri request) path)
      (status-handler request)
      (default-handler request))))

(defn init
  []
  (reset! health-check-registry* (HealthCheckRegistry.))
  {:health-check-registry @health-check-registry*})


;#### debug ###################################################################
;(debug/debug-ns *ns*)
