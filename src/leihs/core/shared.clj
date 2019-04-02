(ns leihs.core.shared
  (:require [leihs.core.http-cache-buster2 :as cache-buster]
            hiccup.page))

(defn include-site-css
  []
  (hiccup.page/include-css (cache-buster/cache-busted-path "/my/css/site.css")))

(defn include-font-css
  []
  (hiccup.page/include-css
    "/my/css/fontawesome-free-5.0.13/css/fontawesome-all.css"))

(defn head
  []
  [:head [:meta {:charset "utf-8"}]
   [:meta
    {:name "viewport",
     :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]
   (include-site-css) (include-font-css)])


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
