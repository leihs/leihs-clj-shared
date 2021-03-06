(ns leihs.core.remote-navbar.front
  (:require-macros
    [reagent.ratom :as ratom]
    [cljs.core.async.macros :refer [go]])
  (:require
    [leihs.core.paths :refer [path]]
    [cljs-http.client :as http-client]
    [reagent.core :as reagent]))

(def navbar* (reagent/atom nil))

(defn nav-component []
  [:div {:dangerouslySetInnerHTML {:__html @navbar*}}])

(defn init []
  (go (let [response (<! (http-client/get "/my/navbar"))]
        (if (= (:status response) 200)
          (reset! navbar* (:body response))))))
