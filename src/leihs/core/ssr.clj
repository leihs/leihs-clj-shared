(ns leihs.core.ssr
  (:refer-clojure :exclude [str keyword])
  (:require
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as log]
    [hiccup.page :refer [html5 include-js]]
    [leihs.core
     [http-cache-buster2 :as cache-buster]
     [release :as release]
     [sql :as sql]]
    [leihs.core.remote-navbar.shared :refer [navbar-props]]
    [leihs.core.shared :refer [head]]
    [leihs.core.ssr-engine :as js-engine]))

(def render-page-base
  "Each subapp MUST INTERN the respective function
  implementation for THIS VAR in THIS NAMESPACE!"
  nil)

(defn- auth-systems
  [tx]
  (->
    (sql/select
      :id :name
      :description :type
      :priority :shortcut_sign_in_enabled)
    (sql/from :authentication_systems)
    (sql/where [:= :enabled true])
    sql/format
    (->>
      (jdbc/query tx))))

(defn render-navbar
  ([request]
   (render-navbar request {}))
  ([request subapps-override]
   (as-> request <>
     (navbar-props <> subapps-override)
     (js-engine/render-react "Navbar" <>))))

(defn render-root-page
  [request]
  (->>
    request
    navbar-props
    (hash-map :navbar)
    (merge {:footer {:appVersion release/version}})
    (js-engine/render-react "HomePage")
    render-page-base))

(defn render-page-by-name
  [request page-name page-props]
  (as-> request <>
    (navbar-props <>)
    (hash-map :navbar <>)
    (merge {:footer {:appVersion release/version}} <> page-props)
    (js-engine/render-react page-name <>)
    (render-page-base <>)))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
