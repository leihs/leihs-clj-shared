(ns leihs.core.ring-exception
  (:refer-clojure :exclude [str keyword])
  (:require [leihs.core.core :refer [keyword str presence]])
  (:require
   [cuerdas.core :as string]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug :refer [I>]]
   [logbug.ring :refer [wrap-handler-with-logging]]
   [logbug.thrown :as thrown]
   [taoensso.timbre :refer [error warn info debug spy]]))

(defn get-cause [e]
  "Deprecated"
  (try (if (instance? java.sql.BatchUpdateException e)
         (if-let [n (.getNextException e)]
           (get-cause n) e)
         (if-let [c (.getCause e)]
           (get-cause c) e))
       (catch Throwable _ e)))

(defn logstr [e]
  ; TODO poosible iterate over exception when (instance? java.sql.SQLException)
  (-> (str (.getMessage e) " "
           (with-out-str
             (clojure.stacktrace/print-cause-trace e)))
      (string/replace "\n" " <<< ")
      (string/collapse-whitespace)))

(defn exception-response [e]
  (cond
    (and (instance? clojure.lang.ExceptionInfo e)
         (contains? (ex-data e)
                    :status)) {:status (:status (ex-data e))
                               :headers {"Content-Type" "text/plain"}
                               :body (.getMessage e)}
    (instance? org.postgresql.util.PSQLException
               e) {:status 409
                   :body (.getMessage e)}
    :else {:status 500
           :headers {"Content-Type" "text/plain"}
           :body "Unclassified error, see the server logs for details."}))

(defn wrap [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable e
        (let [resp (exception-response e)]
          (case (:status resp)
            (401 403) (warn (ex-message e))
            (do #_(error (ex-message e) (ex-data e) (logstr e) {:request request})
             (error e) (error {:request request}))) ; much more readable
          resp)))))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
