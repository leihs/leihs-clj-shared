(ns leihs.core.ssr
  (:refer-clojure :exclude [str keyword])
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [hiccup.page :refer [html5 include-js]]
            [leihs.core
             [http-cache-buster2 :as cache-buster]
             [release :as release]
             [sql :as sql]]
            [leihs.core.remote-navbar.shared :refer [navbar-props]]
            [leihs.core.shared :refer [head]]
            [leihs.core.ssr-engine :as js-engine]))

(defn- render-page-base
  [inner-html]
  (html5 (head)
         [:body {:class "bg-paper"}
          [:noscript "This application requires Javascript."] inner-html
          (hiccup.page/include-js (cache-buster/cache-busted-path
                                    "/my/leihs-shared-bundle.js"))]))

(defn- auth-systems
  [tx]
  (-> (sql/select :id :name
                  :description :type
                  :priority :shortcut_sign_in_enabled)
      (sql/from :authentication_systems)
      (sql/where [:= :enabled true])
      sql/format
      (->> (jdbc/query tx))))

(defn render-navbar
  [request]
  (->> request
       navbar-props
       (js-engine/render-react "Navbar")))

(defn render-root-page
  [request]
  (->> request
       navbar-props
       (hash-map :navbar)
       (merge {:footer {:appVersion release/version}})
       (js-engine/render-react "HomePage")
       render-page-base))

(defn render-sign-in-page
  ([user-param request] (render-sign-in-page user-param request {}))
  ([user-param request extra-props]
   (let [tx (:tx request)
         auth-entity (:authenticated-entity request)]
     (render-page-base
       (js-engine/render-react "SignInPage"
                               (merge {:navbar (navbar-props request),
                                       :authFlow {:user user-param}}
                                      extra-props))))))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
