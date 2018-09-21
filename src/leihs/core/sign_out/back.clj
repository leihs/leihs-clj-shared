(ns leihs.core.sign-out.back
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.core :refer [str keyword presence presence!]]
    [leihs.core.sql :as sql]
    [leihs.core.constants :refer [USER_SESSION_COOKIE_NAME]]

    [clojure.java.jdbc :as jdbc]

    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    )
  (:import
    [java.util UUID]
    ))


(defn ring-handler [{tx :tx
                     {user-session-id :user_session_id} :authenticated-entity
                     :as request}]
  (if-let [user-session-id (-> request
                               :authenticated-entity
                               :user_session_id)]
    (do (jdbc/delete! tx :user_sessions ["id = ?" user-session-id])
        {:status 200
         :body {}
         :cookies
         {leihs.core.constants/USER_SESSION_COOKIE_NAME
          {:value nil
           :http-only true
           :max-age -1
           :path "/"}}})
    {:status 422}))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
