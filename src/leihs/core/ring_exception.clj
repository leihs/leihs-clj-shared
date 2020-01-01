(ns leihs.core.ring-exception
  (:refer-clojure :exclude [str keyword])
  (:require [leihs.core.core :refer [keyword str presence]])
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
  ))

(defn get-cause [e]
  (try (if (instance? java.sql.BatchUpdateException e)
         (if-let [n (.getNextException e)]
           (get-cause n) e)
         (if-let [c (.getCause e)]
           (get-cause c) e))
       (catch Throwable _ e)))

(defn log [_e]
  (let [e (get-cause _e)]
    (logging/warn (thrown/to-string e))
    (logging/debug e)))

(defn exception-response
  ([_e]
   (exception-response _e nil))
  ([_e request]
   (if request
     (logging/warn (str "Exception caught for uri: " (:uri request))))
   (log _e)
   (let [e (get-cause _e)]
     (cond
       (and (instance? clojure.lang.ExceptionInfo e)
            (contains? (ex-data e) :status))
       {:status (:status (ex-data e))
        :headers {"Content-Type" "text/plain"}
        :body (.getMessage e)}
       (instance? org.postgresql.util.PSQLException e)
       {:status 409
        :body (.getMessage e)}
       :else {:status 500
              :headers {"Content-Type" "text/plain"}
              :body "Unclassified error, see the server logs for details."}))))

(defn wrap [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable e
        (exception-response e)))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
