(ns leihs.core.sign-in.simple-login
  (:require
   [hiccup.page :refer [html5]]))

; Designless (but functional) login page
; Does not support external auth and password reset.
; To be used by sub apps other than `my` (which implements the fully-fledged login page).

(defn sign-in-view [params]
  (html5
   [:head
    [:style "body { font-family: sans-serif; width: 400px; margin: 2rem auto; text-align: center; }
             * { margin: 0.5rem; }
             input { width: 15rem; }
             .message { background-color: pink; padding: 1rem; }"]]
   [:body
    [:h1 "Leihs Simple Login"]
    [:p "For testing only"]
    [:form {:class "ui-form-signin"
            :method "POST"
            :action "/sign-in"}

     (for [message (:flashMessages params)]
       [:div.message (:level message) ": " (:messageID message)])

     [:div
      [:label
       "Username"
       [:input {:type :text :name "user" :value (-> params :authFlow :user)}]]]
     [:div
      [:label
       "Password"
       [:input {:type :password :name "password" :value ""}]]]

     [:input {:type :hidden :name (-> params :csrfToken :name) :value (-> params :csrfToken :value)}]
     (when-let [return-to (-> params :authFlow :returnTo)]
       [:input {:type :hidden :name "return-to" :value return-to}])

     [:div
      [:button {:type "submit"}
       "Continue"]]]]))