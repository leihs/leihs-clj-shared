(ns leihs.core.sign-out.back
  (:refer-clojure :exclude [str keyword])
  (:require
   [clojure.java.jdbc :as jdbc]
   [compojure.core :as cpj]
   [leihs.core.constants :refer [USER_SESSION_COOKIE_NAME]]
   [leihs.core.core :refer [str keyword presence presence!]]
   [leihs.core.locale :refer [get-user-db-language set-language-cookie]]
   [leihs.core.paths :refer [path]]
   [leihs.core.sign-in-sign-out.external-authentication
    :refer [create-signed-token]]
   [leihs.core.sql :as sql]
   [leihs.core.url.query-params :as query-params]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug]
   [ring.util.response :refer [redirect]]
   [taoensso.timbre :refer [debug error info spy warn]])
  (:import
   [java.util UUID]))

(defn- delete-user-session [tx id]
  (jdbc/delete! tx :user_sessions ["id = ?" id]))

(defn auth-system-query [user-session-id]
  (-> (sql/select :authentication_systems.*)
      (sql/from :authentication_systems)
      (sql/merge-join :user_sessions [:= :authentication-systems.id
                                      :user_sessions.authentication_system_id])
      (sql/merge-where [:= :user_sessions.id user-session-id])))

(defn prepare-sso-sign-out-token
  [home-url {tx :tx
             {user-session-id :user_session_id
              :as authenticated-entity} :authenticated-entity
             :as request}]
  (if-let [authentication-system (some-> user-session-id
                                         auth-system-query
                                         sql/format
                                         (->> (jdbc/query tx) first))]
    (create-signed-token
     (merge
      {:back_to home-url}
      (select-keys authenticated-entity [:email :external_session_id]))
     authentication-system)
    (throw (ex-info "authentication-system not found" {:user_session_id user-session-id}))))

(defn redirect-sign-out-response
  [{tx :tx
    authenticated-entity :authenticated-entity
    :as request}]
  (let [user-db-language (get-user-db-language request)
        home-url (str (-> request :settings :external_base_url) (path :home))
        redirect-resp (redirect
                       (if-let [sign-out-url (:external_sign_out_url authenticated-entity)]
                         (str sign-out-url "?"
                              (query-params/encode
                               {:token (prepare-sso-sign-out-token home-url request)}))
                         home-url))]
    (when-let [user-session-id (:user_session_id authenticated-entity)]
      (delete-user-session tx user-session-id))
    ; Always redirect to home, even if already logged out and
    ; set leihs-user-locale cookie if user has a preferred language.
    (cond-> redirect-resp
      user-db-language (set-language-cookie user-db-language))))

(defn ring-handler [request]
  (if (= (-> request :accept :mime) :json)
    {:status 406}
    (redirect-sign-out-response request)))

(def routes
  (cpj/routes
   (cpj/POST (path :sign-out) [] #'ring-handler)))

;#### debug ###################################################################
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
