(ns leihs.core.sign-in.simple-login
  (:require
   [hiccup.page :refer [html5]]))

; Designless (but functional) login page
; Drop-in replacement for the actual view (implemented by `leihs-ui`, rendered via SSR)
; Does not support external auth and password reset.
; 
; This is a patch to make tests work in `leihs-borrow` after being decoupled from `leihs-ui`.
; 
; IMPROVE: This is now plugged into the primary sign-in handler (sign-in/back.cljs) instead of defining its 
; own handler. However this issue must be addressed in the course of decoupling `leihs-my`
; from `leihs-ui`.

(defn sign-in-view [params]
  (html5
   [:head
    [:style "body { font-family: sans-serif; width: 400px; margin: 2rem auto; text-align: center; }
             * { margin: 0.5rem; }
             input { width: 15rem; }"]]
   [:body
    [:h1 "Leihs Simple Login"]
    [:p "For testing only"]
    [:form {:class "ui-form-signin"
            :method "POST"
            :action "/sign-in"}

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
       "Weiter"]]]]))