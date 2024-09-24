(ns leihs.core.cli
  (:refer-clojure :exclude [str])
  (:require
   [cuerdas.core :as string :refer [kebab snake upper]]
   [leihs.core.core :refer [str]]))

(defn long-opt-for-key [k]
  (str "--" (kebab k) " " (-> k snake upper)))
