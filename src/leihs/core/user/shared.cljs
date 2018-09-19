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
