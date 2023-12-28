(ns leihs.core.sign-out.sso
  (:refer-clojure :exclude [str keyword])
  (:require
   [clojure.java.jdbc :as jdbc]
   [compojure.core :as cpj]
   [leihs.core.core :refer [str keyword presence presence!]]
   [leihs.core.paths :refer [path]]
   [leihs.core.sign-in-sign-out.external-authentication
    :refer [unsign-external-token]]
   [leihs.core.sql :as sql]
   [ring.util.response :refer [redirect]]
   [taoensso.timbre :refer [debug error info spy warn]]))

(defn authentication-system [id tx]
  (-> (sql/select :authentication_systems.*)
      (sql/from :authentication_systems)
      (sql/merge-where [:= :authentication_systems.id id])
      sql/format
      (->> (jdbc/query tx) first)))

(defn sso-sign-out [{{token :token} :query-params-raw
                     {authentication-system-id :authentication-system-id}  :route-params
                     {base-url :external_base_url} :settings
                     tx :tx
                     :as request}]
  (when-let [authentication-system (authentication-system authentication-system-id tx)]
    (let [{external-session-id
           :external_session_id} (unsign-external-token token authentication-system)]
      (jdbc/delete!
       tx :user_sessions
       ["authentication_system_id = ? AND external_session_id = ?"
        authentication-system-id external-session-id])
      (redirect (str base-url (path :home))))))

(def routes
  (cpj/routes
   (cpj/ANY (path :external-authentication-sso-sign-out
                  {:authentication-system-id ":authentication-system-id"})
     [] #'sso-sign-out)))
