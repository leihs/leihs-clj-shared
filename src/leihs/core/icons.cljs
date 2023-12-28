(ns leihs.core.icons
  (:refer-clojure :exclude [str keyword])
  (:require
   ["@fortawesome/free-brands-svg-icons" :as brands]
   ["@fortawesome/free-solid-svg-icons" :as solids]
   ["@fortawesome/react-fontawesome" :as fa-react-fontawesome :refer [FontAwesomeIcon]]
   [leihs.core.core :refer [keyword str presence]]))

(defn admin [] (FontAwesomeIcon #js{:icon solids/faWrench :className ""}))
(defn delete [] (FontAwesomeIcon #js{:icon solids/faTimes :className ""}))
(defn home [] (FontAwesomeIcon #js{:icon solids/faHome :className ""}))



