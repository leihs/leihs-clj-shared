(ns leihs.core.global
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [leihs.core.core :refer [keyword str presence]]

    [cljs.core.async :as async]
    [cljsjs.moment]
    [clojure.pprint :refer [pprint]]
    [reagent.core :as reagent]
    ))


(def timestamp* (reagent/atom (js/moment)))

(defn init []
  (go
    (loop []
      (async/<! (async/timeout 250))
      (reset! timestamp* (js/moment))
      (recur))))

