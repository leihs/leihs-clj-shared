(ns leihs.core.sign-out.back
  (:refer-clojure :exclude [str keyword])
  (:require [clojure.tools.logging :as log]

            [leihs.core.core :refer [str keyword presence presence!]]
            [leihs.core.paths :refer [path]]
            [leihs.core.sql :as sql]
            [leihs.core.constants :refer [USER_SESSION_COOKIE_NAME]]
            [clojure.java.jdbc :as jdbc]
            [logbug.catcher :as catcher]
            [logbug.debug :as debug]
            [ring.util.response :refer [redirect]])
  (:import
   [java.util UUID]))

(defn- delete-user-session [tx id]
  (jdbc/delete! tx :user_sessions ["id = ?" id]))

(defn ring-handler [{tx :tx
                     {user-session-id :user_session_id} :authenticated-entity
                     :as request}]
  (let [user-session-id (-> request :authenticated-entity :user_session_id)
        accept-type (-> request :accept :mime)]
    (if (= accept-type :json)
      (if user-session-id
        (do (delete-user-session tx user-session-id)
            {:status 200
             :body {}
             :cookies
             {leihs.core.constants/USER_SESSION_COOKIE_NAME
              {:value nil
               :http-only true
               :max-age -1
               :path "/"}}})
        {:status 422})
      (do (if user-session-id
            (delete-user-session tx user-session-id))
          (redirect (path :home))))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
