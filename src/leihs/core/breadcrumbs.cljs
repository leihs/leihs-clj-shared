(ns leihs.core.breadcrumbs
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.auth.core :as auth]
    [leihs.core.icons :as icons]
    [leihs.core.paths :as paths :refer [path]]
    [leihs.core.routing.front :as routing]
    [leihs.core.user.front :as current-user]

    [reagent.core :as reagent]
    ))

(defn active [handler-key]
  (= (-> @routing/state* :handler-key) handler-key))

(def all-granted (constantly true))

(def base-button-classes "btn btn-sm pt-0 pb-0 pl-1 pr-1")

(def enabled-button-classes
  (clojure.string/join
    " " [base-button-classes "btn-outline-primary"]))

(def disabled-button-classes
  (clojure.string/join
    " " [base-button-classes "btn-outline-dark disabled"]))

(defn li
  ([k n]
   (li k n {} {}))
  ([handler-key-or-full-path inner route-params query-params
    & {:keys [button authorizers link-disabled]
       :or {button false
            link-disabled false
            authorizers [;all-granted
                         ]}}]
   (let [active? (active handler-key-or-full-path)]
     (when (auth/allowed? authorizers)
       [:li.breadcrumb-item {:key handler-key-or-full-path
                             :class (if active? "active" "")}
        (if (or link-disabled active?)
          (if-not button
            [:span inner]
            [:a
             {:class disabled-button-classes
              :disabled true}
             inner])
          [:a {:class (when button enabled-button-classes)
               :href (if (string? handler-key-or-full-path)
                       handler-key-or-full-path
                       (path handler-key-or-full-path
                             route-params query-params))} inner])]))))

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

(defn admin-li []
  [li :admin [:span [icons/admin] " Admin "] {} {}
   :authorizers [auth/all-granted]])


(defn borrow-li [] (li :borrow "Borrow"))

(defn debug-li [] (li :debug "Debug"))

(defn initial-admin-li [] (li :initial-admin "Initial-Admin"))

(defn leihs-li []
  [li :home [:span [icons/home] " Home "] {} {}
   :authorizers [auth/all-granted]])

(defn lending-li []
  [li :lending "Lending"])

(defn procurement-li [] (li :procurement "Procurement"))
