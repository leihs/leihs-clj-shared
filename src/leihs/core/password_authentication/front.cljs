(ns leihs.core.password-authentication.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.constants]

    [clojure.pprint :refer [pprint]]
    [accountant.core :as accountant]
    [reagent.core :as reagent]
    ))

(defn sign-in-component [path & [pws]]
  (let [pws (or pws {:id "password",
                     :type "password",
                     :name "leihs password",
                     :index 0,
                     :key "password"})
        password* (reagent/atom "")
        disabled?* (reaction (-> @password* presence boolean not))]
    (fn []
      [:div.password-authentication-system
       {:id (:id pws)
        :key (:key pws)}
       ;[:pre (with-out-str (pprint pws))]
       [:div.card {:key (:key pws)}
        [:div.card-header
         {:class (case (:index pws)
                   0 "text-white bg-primary"
                   "")}
         [:h2 [:i.fas.fa-key] " Sign in with \"" (:name pws) "\""]]
        [:div.card-body
         [:form.form.form-inline
          {:method :post
           :action path}
          [:div.form-group.mx-sm-2.mb-2
           [:input
            {:type :hidden
             :auto-complete :email
             :name :email
             :value ""}]]
          [:div.form-group.mx-sm-2.mb-2
           [:input#password
            {:type :password
             :auto-complete :password
             :name :password
             :value @password*
             :on-change #(reset! password* (-> % .-target .-value))}]]
          [:button.btn.mb-2
           {:disabled @disabled?*
            :class (if (= 0 (:index pws))
                     "btn-primary"
                     "btn-secondary")
            :type :submit}
           "Sign in"]]]]])))
