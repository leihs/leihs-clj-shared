(ns leihs.core.anti-csrf.front
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.constants :as constants]
    [goog.net.cookies]
    ))

(defn anti-csrf-token []
  (.get goog.net.cookies constants/ANTI_CSRF_TOKEN_COOKIE_NAME))

(defn hidden-form-group-token-component []
  [:div.form-group
   [:input
    {:type :hidden
     :name constants/ANTI_CSRF_TOKEN_FORM_PARAM_NAME
     :value (anti-csrf-token)}]])
