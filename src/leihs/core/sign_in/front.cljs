(ns leihs.core.sign-in.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.constants]
    [leihs.core.paths :refer [path]]
    [leihs.core.routing.front :as routing]
    [leihs.core.global :as global]

    [clojure.pprint :refer [pprint]]
    [accountant.core :as accountant]
    [reagent.core :as reagent]
    ))


(defn nav-email-continue-component []
  (let [email* (reagent/atom (or (-> @routing/state* :query-params-raw :email presence)
                                 ""))
        valid?* (reaction (boolean (re-matches #".+@.+" @email*)))]
    (fn []
      [:form.form-inline
       [:div.form-group
        [:label.sr-only {:for :email} "email "]
        [:input#email.email.form-control.mr-sm-2
         {:type :email
          :placeholder "email address"
          :auto-complete :email
          :value @email*
          :on-change #(reset! email* (-> % .-target .-value))}]]
       [:input#password.email.form-control.mr-sm-2
        {:style {:display :none}
         :type :password
         :auto-complete :current-password
         :placeholder "password"}]
       [:a.btn.btn-primary
        {:class (when-not @valid?* "disabled")
         :href (path :sign-in {} {:email @email* :timestamp (.format @global/timestamp*)})
         :type :submit}
        [:i.fas.fa-sign-in]
        " Continue to sign in" ]])))

