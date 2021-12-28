(ns leihs.core.routing.dispatch-content-type
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.core :refer [keyword str presence]]
    [leihs.core.json :as json]
    [leihs.core.json-protocol]
    [ring.middleware.accept]
    ))


(defn wrap-accept [handler]
  (ring.middleware.accept/wrap-accept
    handler
    {:mime
     ["application/json" :qs 1 :as :json
      "image/apng" :qs 0.8 :as :apng
      "text/css" :qs 1 :as :css
      "text/html" :qs 1 :as :html]}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- browser-request-matches-javascript? [request]
  "Returns true if the accepted type is javascript or
  if the :uri ends with .js. Note that browsers do not
  use the proper accept type for javascript script tags."
  (boolean (or (= (-> request :accept :mime) :javascript)
               (re-find #".+\.js$" (or (-> request :uri presence) "")))))

(defn wrap-dispatch-html
  "Shortcuts to the html handler."
  ([handler no-html-handler-keys html-handler]
   (fn [request]
     (wrap-dispatch-html handler no-html-handler-keys html-handler request)))
  ([handler no-html-handler-keys html-handler request]
   (cond
     ; accept json always goes to the backend handlers, i.e. the normal routing
     (= (-> request :accept :mime) :json) (or (handler request)
                                              (throw (ex-info "This resource does not provide a json response."
                                                              {:status 406})))
     ; accept HTML and GET (or HEAD) wants allmost always the frontend
     (and (= (-> request :accept :mime) :html)
          (#{:get :head} (:request-method request))
          (not (no-html-handler-keys (:handler-key request)))
          (not (browser-request-matches-javascript? request))
          ) (html-handler request)
     ; other request might need to go the backend and return frontend notheless
     :else (let [response (handler request)]
             (if (and (nil? response)
                      (not (no-html-handler-keys (:handler-key request)))
                      (not (#{:post :put :patch :delete} (:request-method request)))
                      (= (-> request :accept :mime) :html)
                      (not (browser-request-matches-javascript? request)))
               (html-handler request)
               response)))))

