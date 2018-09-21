(ns leihs.core.user.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [leihs.core.constants]
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.dom :as dom]
    [leihs.core.paths :refer [path]]
    [leihs.core.requests.core :as requests]
    [leihs.core.routing.front :as routing]
    [leihs.core.sign-in.front :as sign-in]
    [leihs.core.sign-out.front :as sign-out]

    [cljs.core.async :as async]
    [cljsjs.moment]
    [clojure.pprint :refer [pprint]]
    [goog.string :as gstring]
    [reagent.core :as reagent]
    ))

(def state* (reagent/atom nil))

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


(defn navbar-user-nav []
  (reagent/create-class
    {:component-did-mount load-user-data-from-dom
     :reagent-render
     (fn [_]
       (if-let [user @state*]
         [:div.navbar-nav.user-nav
          [:div
           [:a
            {:href (path :me-user {} {})}
            [:span
             [:img.user-img-32
              {:width 32
               :height 32
               :src (or (:img32_url user)
                        (gravatar-url (:email user)))}]
             [:span.sr-only (:email user)]]]]
          [sign-out/component]]
         (when-not (#{:initial-admin :sign-in}
                                     (-> @routing/state* :handler-key))
           [:div.navbar-nav
            [sign-in/nav-email-continue-component]])))}))

