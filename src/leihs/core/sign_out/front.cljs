(ns leihs.core.sign-out.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [leihs.core.constants]
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.paths :refer [path]]
    [leihs.core.requests.core :as requests]
    [leihs.core.user.shared :refer [state*]]
    [leihs.core.routing.front :as routing]

    [cljs.core.async :as async]
    ))


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


(defn component []
  [:form.form-inline.ml-2
   {:on-submit (fn [e]
                 (.preventDefault e)
                 (sign-out))}
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

