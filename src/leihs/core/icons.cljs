(ns leihs.core.icons
  (:refer-clojure :exclude [str keyword])
  (:require
   ["@fortawesome/free-solid-svg-icons" :as solids]
   ["@fortawesome/react-fontawesome" :as fa-react-fontawesome :refer [FontAwesomeIcon]]))

(defn admin [] [:> FontAwesomeIcon {:icon solids/faWrench :className ""}])
(defn delete [] [:> FontAwesomeIcon {:icon solids/faTimes :className ""}])
(defn home [] [:> FontAwesomeIcon {:icon solids/faHome :className ""}])



