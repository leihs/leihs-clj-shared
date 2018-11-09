(ns leihs.core.breadcrumbs
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.icons :as icons]
    [leihs.core.paths :as paths :refer [path]]
    [leihs.core.routing.front :as routing]
    ))

(defn active? [handler-key]
  (= (-> @routing/state* :handler-key) handler-key))

(defn li
  ([k n]
   (li k n {} {}))
  ([handler-key-or-full-path inner route-params query-params]
   (let [active? (active? handler-key-or-full-path)]
     [:li.breadcrumb-item {:key handler-key-or-full-path :class (if active? "active" "")}
      (if active? 
        [:span inner]
        [:a {:href (if (string? handler-key-or-full-path)
                     handler-key-or-full-path
                     (path handler-key-or-full-path route-params query-params))} inner])])))

(defn nav-component [left right]
  [:div.row.nav-component.mt-3
   [:nav.col-lg {:aria-label :breadcrumb :role :navigation}
    (when (seq left)
      [:ol.breadcrumb
       (for [li left] li) ])]
   [:nav.col-lg {:role :navigation}
    (when (seq right)
      [:ol.breadcrumb.leihs-nav-right
       (for [li right] li)])]])

(defn admin-li [] (li :admin [:span icons/admin " Admin "]))
;(defn me-user-li [] (li :me-user [:span icons/user " User "]))
(defn borrow-li [] (li :borrow "Borrow"))
(defn debug-li [] (li :debug "Debug"))
(defn initial-admin-li [] (li :initial-admin "Initial-Admin"))
(defn leihs-li [] [:li.breadcrumb-item [:a {:href (path (:home)), :data-trigger true}]])
(defn lending-li [] (li :lending "Lending"))
(defn procurement-li [] (li :procurement "Procurement"))
