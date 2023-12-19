(ns leihs.core.sign-out.back
  (:refer-clojure :exclude [str keyword])
  (:require
    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]
    [leihs.core.constants :refer [USER_SESSION_COOKIE_NAME]]
    [leihs.core.core :refer [str keyword presence presence!]]
    [leihs.core.locale :refer [get-user-db-language set-language-cookie]]
    [leihs.core.paths :refer [path]]
    [leihs.core.sql :as sql]
    [leihs.core.url.query-params :as query-params]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [ring.util.response :refer [redirect]])
  (:import
   [java.util UUID]))

(defn- delete-user-session [tx id]
  (jdbc/delete! tx :user_sessions ["id = ?" id]))


(defn redirect-sign-out-response
  [{tx :tx-next
    authenticated-entity :authenticated-entity
    :as request}]
  (let [user-db-language (get-user-db-language request)
        home-url (str (-> request :settings :external_base_url) (path :home))
        redirect-resp (redirect
                        (if-let [sign-out-url (:external_sign_out_url authenticated-entity)]
                          (str sign-out-url "?" (query-params/encode {:back_to home-url}))
                          home-url))]
    (when-let [user-session-id (:user_session_id authenticated-entity)]
      (delete-user-session tx user-session-id))
    ; Always redirect to home, even if already logged out and
    ; set leihs-user-locale cookie if user has a preferred language.
    (cond-> redirect-resp
      user-db-language (set-language-cookie user-db-language))))

(defn ring-handler [request]
  (if (= (-> request :accept :mime) :json)
    {:status 406}
    (redirect-sign-out-response request)))

(def routes
  (cpj/routes
    (cpj/POST (path :sign-out) [] #'ring-handler)))


;#### debug ###################################################################
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
