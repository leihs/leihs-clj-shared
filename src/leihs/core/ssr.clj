(ns leihs.core.ssr
  (:refer-clojure :exclude [str keyword])
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [leihs.core.anti-csrf.back :refer [anti-csrf-props]]
   [leihs.core.release :as release]
   [leihs.core.remote-navbar.shared :refer [navbar-props]]
   [leihs.core.ssr-engine :as js-engine]
   [next.jdbc.sql :refer [query] :rename {query jdbc-query}]
   [taoensso.timbre :as log :refer [info]]))

(def render-page-base*
  (atom (fn [_inner]
          (throw (ex-info
                  "No implementaion for render-page-base provided yet"
                  {})))))

(defn render-page-base [inner]
  (@render-page-base* inner))

(defn- auth-systems [tx]
  (-> (sql/select :id :name
                  :description :type
                  :priority :shortcut_sign_in_enabled)
      (sql/from :authentication_systems)
      (sql/where [:= :enabled true])
      sql-format
      (->> (jdbc-query tx))))

(defn render-navbar
  ([request]
   (render-navbar request {}))
  ([request subapps-override]
   (as-> request <>
     (navbar-props <> subapps-override)
     (js-engine/render-react "Navbar" <>))))

(defn render-page-by-name
  [request page-name page-props]
  (as-> request <>
    (navbar-props <>)
    (hash-map :navbar <>)
    (merge (anti-csrf-props request) <>)
    (merge {:footer {:appVersion release/version}} <> page-props)
    (js-engine/render-react page-name <>)
    (render-page-base <>)))

(defn init [render-page-base-fn]
  (info "initializing core.ssr, setting implementation for render-page-base*")
  (assert (fn? render-page-base-fn))
  (reset! render-page-base* render-page-base-fn)
  (info "initialized core.ssr"))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
