(ns leihs.core.anti-csrf.front
  (:refer-clojure :exclude [str keyword])
  (:require
   [goog.net.Cookies]
   [leihs.core.constants :as constants]
   [leihs.core.core :refer [keyword str presence]]))

(defonce ^:dynamic *cookies* (or goog.net.cookies (.getInstance goog.net.Cookies)))

(defn anti-csrf-token []
  (.get *cookies* constants/ANTI_CSRF_TOKEN_COOKIE_NAME))

(defn hidden-form-group-token-component []
  [:div.form-group
   [:input
    {:type :hidden
     :name constants/ANTI_CSRF_TOKEN_FORM_PARAM_NAME
     :value (anti-csrf-token)}]])
