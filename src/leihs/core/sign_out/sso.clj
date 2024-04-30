(ns leihs.core.sign-out.sso
  (:refer-clojure :exclude [str keyword])
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [leihs.core.sign-in-sign-out.external-authentication
    :refer [unsign-external-token]]
   [next.jdbc.sql :refer [delete! query] :rename {query jdbc-query,
                                                  delete! jdbc-delete!}]
   [taoensso.timbre :refer [warn]]))

(defn authentication-system [id tx]
  (-> (sql/select :authentication_systems.*)
      (sql/from :authentication_systems)
      (sql/where [:= :authentication_systems.id id])
      sql-format
      (->> (jdbc-query tx) first)))

(defn sso-sign-out [{{token :token} :query-params-raw
                     {authentication-system-id :authentication-system-id}  :route-params
                     {base-url :external_base_url} :settings
                     tx :tx
                     :as request}]
  (if-let [authentication-system (authentication-system authentication-system-id tx)]
    (let [{external-session-id
           :external_session_id} (unsign-external-token token authentication-system)]
      (jdbc-delete!
       tx :user_sessions
       ["authentication_system_id = ? AND external_session_id = ?"
        authentication-system-id external-session-id]))
    (warn (format "no matching authentication system '%s' for sso sign-out"
                  authentication-system-id)))
  {:status 204
   :headers {"content-type" "text/plain"}
   :body nil})

(def routes sso-sign-out)

;#### debug ###################################################################
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
