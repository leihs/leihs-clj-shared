(ns leihs.core.settings
  (:refer-clojure :exclude [str keyword])
  (:require [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [leihs.core.core :refer [keyword str presence]]
            [logbug.debug :as debug]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :refer [query] :rename {query jdbc-query}]
            [taoensso.timbre :as log :refer [error warn info debug spy]]))

(def selected-columns
  [:email_signature
   :system_and_security_settings.external_base_url
   :system_and_security_settings.sessions_force_secure
   :system_and_security_settings.sessions_force_uniqueness
   :system_and_security_settings.sessions_max_lifetime_secs
   :system_and_security_settings.public_image_caching_enabled
   [:smtp_settings.default_from_address :smtp_default_from_address]
   [:smtp_settings.enabled :email_sending_enabled]])

(defn settings-base-query
  ([] (settings-base-query selected-columns))
  ([columns]
   (-> (apply sql/select columns)
       (sql/from :settings)
       (sql/join :system_and_security_settings
                 [:= :settings.id :system_and_security_settings.id])
       (sql/join :smtp_settings
                 [:= :settings.id :smtp_settings.id]))))

(defn settings!
  ([tx] (settings! tx selected-columns))
  ([tx columns]
   (or (-> (settings-base-query columns)
           sql-format
           (->> (jdbc-query tx))
           first)
       (throw (IllegalStateException. "No settings here!")))))

(defn wrap
  ([handler]
   (fn [request]
     (wrap handler request)))
  ([handler request]
   (handler (assoc request :settings (settings! (:tx request))))))

;#### debug ###################################################################
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
