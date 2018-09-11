(ns leihs.core.shutdown
  (:refer-clojure :exclude [str keyword])
  (:require [leihs.core.core :refer [keyword str presence]])
  (:require
    [leihs.core.sql :as sql]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]
    [ring.util.response :refer [redirect]]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug])
  (:import
    [java.util UUID]
    ))

(def enabled* (atom false))

(defn ring-handler [{method :request-method}]
  (cond 
    (not @enabled*) {:staus 403}
    (not= method :post) {:status 405
                         :headers {"allow" "POST"}}
    :else (do (future (Thread/sleep 500)
                      (System/exit 0))
              {:status 204
               :body "shutting down in 500 ms"})))

(defn init [options]
  (when (:enable-shutdown-route options)
    (reset! enabled* true)))
