(ns leihs.core.cli
  (:require
   [cuerdas.core :as string :refer [kebab snake upper]]
   [leihs.core.core :refer [str]]))

(defn long-opt-for-key [k]
  (str "--" (kebab k) " " (-> k snake upper)))
