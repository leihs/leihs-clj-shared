(ns leihs.core.user.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [leihs.core.constants]
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.digest]
    [leihs.core.dom :as dom]
    [leihs.core.paths :refer [path]]
    [leihs.core.requests.core :as requests]
    [leihs.core.routing.front :as routing]
    [leihs.core.sign-in.front :as sign-in]
    [leihs.core.sign-out.front :as sign-out]
    [leihs.core.user.shared :as shared]
    [cljs.core.async :as async]
    [clojure.pprint :refer [pprint]]
    [goog.string :as gstring]
    [reagent.core :as reagent]
    ))


(def state* shared/state*)

(def admin?* (reaction (:is_admin @state*)))
(def system-admin?* (reaction (:is_system_admin @state*)))

;(defonce admin-scopes?* (reaction (auth-core/admin-scopes? @state* nil)))

(defn load-user-data-from-dom [& args]
  (when-not @state*
    (reset! state* (dom/data-attribute "body" "user"))))

(defn gravatar-url
  ([email]
   (gravatar-url email 32))
  ([email size]
   (if-not (presence email)
     (gstring/format
       "https://www.gravatar.com/avatar/?s=%d&d=blank" size)
     (let [md5 (->> email
                    clojure.string/trim
                    clojure.string/lower-case
                    leihs.core.digest/md5-hex)]
       (gstring/format
         "https://www.gravatar.com/avatar/%s?s=%d&d=retro"
         md5 size)))))
