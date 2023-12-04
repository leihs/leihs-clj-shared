(ns leihs.core.global
  (:refer-clojure :exclude [str keyword])
  (:require-macros
   [cljs.core.async.macros :refer [go]]
   [reagent.ratom :as ratom :refer [reaction]])
  (:require
   [cljs.core.async :as async]

   [clojure.pprint :refer [pprint]]
   [leihs.core.core :refer [keyword str presence]]
   [reagent.core :as reagent]))

(def timestamp* (reagent/atom (js/Date.)))

(defn init []
  (go
    (loop []
      (async/<! (async/timeout 250))
      (reset! timestamp* (js/Date.))
      (recur))))

