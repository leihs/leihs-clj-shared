(ns leihs.core.remote-navbar.front
  (:require-macros
   [cljs.core.async.macros :refer [go]]
   [reagent.ratom :as ratom])
  (:require
   [cljs-http.client :as http-client]
   [leihs.core.paths :refer [path]]
   [reagent.core :as reagent]))

(def navbar* (reagent/atom nil))

(defn nav-component []
  [:div {:dangerouslySetInnerHTML {:__html @navbar*}}])

(defn init []
  (go (let [response (<! (http-client/get "/my/navbar"))]
        (if (= (:status response) 200)
          (reset! navbar* (:body response))))))
