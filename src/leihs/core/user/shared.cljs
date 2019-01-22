(ns leihs.core.user.shared
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [leihs.core.core :refer [keyword str presence]]
    
    [reagent.core :as reagent]
    ))

(def state* (reagent/atom nil))

(defn short-id [uuid]
  [:span {:style {:font-family :monospace}}
   (->> uuid (take 8) clojure.string/join)])

(defn user-name-html [user-id user-data]
  (if-not user-data
   [:span {:style {:font-family "monospace"}} (short-id user-id)]
   [:em 
    (if-let [name (-> (str (:firstname user-data) " " (:lastname user-data))
                      clojure.string/trim presence)]
      name
      (:email user-data))] ))
