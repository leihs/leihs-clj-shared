(ns leihs.core.navbar.back
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [hiccup.core :refer [html]]
            [leihs.core.sql :as sql]
            [leihs.core.user.permissions :refer [borrow-access? managed-inventory-pools]]
            [leihs.core.user.permissions.procure :as procure]))

(defn handler [request]
  (let [tx (:tx request)
        auth-entity (:authenticated-entity request)]
    {:headers {"Content-Type" "text/plain"}
     :body (html
             [:ul
              (if (borrow-access? tx auth-entity)
                [:li "Borrow"])
              (if (:is_admin auth-entity)
                [:li "Admin"])
              (if (procure/any-access? tx auth-entity)
                [:li "Procure"])
              (let [pools (managed-inventory-pools tx auth-entity)]
                (if-not (empty? pools)
                  [:div
                   "Pools:"
                   [:ul
                    (map #(->> % :name (vector :li)) pools)]]))])}))
