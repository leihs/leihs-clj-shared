(ns leihs.core.remote-navbar.back
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [hiccup.core :refer [html]]
            [leihs.core.paths :refer [path]]
            [leihs.core.sql :as sql]
            [leihs.core.user.permissions :refer [borrow-access? managed-inventory-pools]]
            [leihs.core.user.permissions.procure :as procure]))

(defn handler [request]
  (let [tx (:tx request)
        auth-entity (:authenticated-entity request)]
    {:headers {"Content-Type" "text/plain"}
     :body (html
             [:h3 {:style "color: red"} "Remote Navbar"]
             [:ul
              (if (borrow-access? tx auth-entity)
                [:li
                 [:a {:href (path :borrow)} "Borrow"]])
              (if (:is_admin auth-entity)
                [:li
                 [:a {:href (path :admin)} "Admin"]])
              (if (procure/any-access? tx auth-entity)
                [:li
                 [:a {:href (path :procurement)} "Procurement"]])
              (let [pools (managed-inventory-pools tx auth-entity)]
                (if-not (empty? pools)
                  [:div
                   "Pools:"
                   [:ul
                    (map #(vector :li
                                  [:a {:href (path :daily {:inventory_pool_id (:id %)})}
                                   (:name %)])
                         pools)]]))])}))
