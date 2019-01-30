(ns leihs.core.sign-out.back
  (:refer-clojure :exclude [str keyword])
  (:require [clojure.tools.logging :as log]
            [leihs.core.core :refer [str keyword presence presence!]]
            [leihs.core.locale :refer [get-user-db-language set-language-cookie]]
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
        accept-type (-> request :accept :mime)
        user-db-language (get-user-db-language request)]
    (if (= accept-type :json)
      (if-not user-session-id
        {:status 422}
        (do (delete-user-session tx user-session-id)
            (let [response {:status 200
                            :body {}
                            :cookies
                            {leihs.core.constants/USER_SESSION_COOKIE_NAME
                             {:value nil
                              :http-only true
                              :max-age -1
                              :path "/"}}}]
              ; Set leihs-user-locale cookie if user has a preferred language.
              (cond-> response
                user-db-language
                (set-language-cookie user-db-language)))))
      (do (if user-session-id
            (delete-user-session tx user-session-id))
          ; Always redirect to home, even if already logged out and
          ; set leihs-user-locale cookie if user has a preferred language.
          (cond-> (redirect (path :home))
            user-db-language
            (set-language-cookie user-db-language))))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
