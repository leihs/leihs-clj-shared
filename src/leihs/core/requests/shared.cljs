(ns leihs.core.requests.shared
  (:refer-clojure :exclude [str keyword send-off])
  (:require-macros
   [cljs.core.async.macros :refer [go]]
   [reagent.ratom :as ratom :refer [reaction]])
  (:require
   [cljs.core.async :refer [timeout]]
   [leihs.core.core :refer [str keyword deep-merge presence]]

   [reagent.core :as reagent]))

(defonce state* (reagent/atom {}))

(defn response-success? [resp]
  (<= 200 (-> resp :status) 299))

(defn bootstrap-status [modal-status]
  (case modal-status
    :pending :default
    :success :success
    :error :danger))

(defn status [request]
  (cond (= nil (-> request :response)) :pending
        (-> request :response :success) :success
        :else :error))

(def fetching?*
  (reaction
   (->> @state*
        :requests
        (map (fn [[id r]] r))
        (map :response)
        (map map?)
        (map not)
        (filter identity)
        first)))

(defn dismiss [request-id]
  (swap! state*
         update-in [:requests]
         (fn [rx] (dissoc rx request-id))))

(defn dismiss-button-component
  ([request]
   (dismiss-button-component request {}))
  ([request opts]
   [:button.btn
    {:class (str "btn-" (-> request status bootstrap-status)
                 " " (:class opts))
     :on-click #(dismiss (:id request))}
    [:i.fas.fa-times] " Dismiss "]))
