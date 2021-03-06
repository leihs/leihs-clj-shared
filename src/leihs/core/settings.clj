(ns leihs.core.settings
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.sql :as sql]

    [clojure.java.jdbc :as jdbc]

    [logbug.debug :as debug]
    [clojure.tools.logging :as logging]
    ))


(def selected-rows
  [:system_and_security_settings.external_base_url
   :system_and_security_settings.sessions_force_secure
   :system_and_security_settings.sessions_force_uniqueness
   :system_and_security_settings.sessions_max_lifetime_secs
   [:smtp_settings.default_from_address :smtp_default_from_address]])

(def settings-base-query
  (-> (apply sql/select selected-rows)
      (sql/from :settings)
      (sql/merge-join :system_and_security_settings
                      [:= :settings.id :system_and_security_settings.id])
      (sql/merge-join :smtp_settings
                      [:= :settings.id :smtp_settings.id])))

(defn settings! [tx]
  (or (->> (-> settings-base-query sql/format)
           (jdbc/query tx) first)
      (throw (IllegalStateException. "No settings here!"))))

(defn wrap
  ([handler]
   (fn [request]
     (wrap handler request)))
  ([handler request]
   (handler (assoc request :settings (settings! (:tx request))))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
