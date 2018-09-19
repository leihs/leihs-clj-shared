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

    [clojure.pprint :refer [pprint]]
    [cljs.core.async :as async]
    [reagent.core :as reagent]
    [goog.string :as gstring]
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

(defn sign-out [& args]
  (defonce sign-out-id* (atom nil))
  (let [resp-chan (async/chan)
        p1 {:url (path :sign-out)
            :method :post
            :json-params {}}
        p2 {:modal false
            :title "Sign out"
            :retry-fn #'sign-out}
        id (requests/send-off p1 p2 :chan resp-chan)]
    (reset! sign-out-id* id)
    (go (let [resp (<! resp-chan)]
          (when (= id @sign-out-id*)
            (case (:status resp)
              200 (do (reset! state* nil)
                      (routing/navigate! (path :home)))))))))


(defn sign-out-component []
  [:form.form-inline.ml-2
   {:on-submit (fn [e]
                 (.preventDefault e)
                 (sign-out))}
   ;[anti-csrf/hidden-form-group-token-component]
   [:div.form-group
    [:label.sr-only
     {:for :sign-out}
     "Sign out"]
    [:button#sign-out.btn.btn-dark.form-group
     {:type :submit
      :style {:padding-top "0.2rem"
              :padding-bottom "0.2rem"}}
     [:span
      [:span " Sign out "]
      [:i.fas.fa-sign-out-alt]]]]])


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
          [sign-out-component]]
         (when-not (#{:initial-admin :sign-in}
                                     (-> @routing/state* :handler-key))
           [:div.navbar-nav
            [sign-in/nav-email-continue-component]])))}))

