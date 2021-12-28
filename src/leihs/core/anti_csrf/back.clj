(ns leihs.core.anti-csrf.back
  "- The `anti-csrf-token` in cookie is a secret Javascript has no access to.
   - The frontend gets directly the same secret.
   - At every request, does not matter from where, the secret cookie is sent
     (done by the browser).
   - BUT: only requests from the frontend have directly got the secret and
     thus can send it back to server via the form data.
   - Then the comparison with the cookie proves the origin of the request."
  (:refer-clojure :exclude [str keyword])
  (:require
    [clojure.tools.logging :as logging]
    [leihs.core.constants :as constants]
    [leihs.core.core :refer [keyword str presence]]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown])
  (:import
    [java.util UUID]))

(defn http-safe? [request]
  (boolean (constants/HTTP_SAVE_METHODS
             (-> request :request-method))))

(def http-unsafe? (complement http-safe?))

(defn token? [request]
  (= :token (-> request :authenticated-entity
                :authentication-method)))

(defn not-authenticated? [request]
  (-> request
      :authenticated-entity
      boolean not))

(defn x-csrf-token! [request]
  (or (-> request :headers (get (keyword constants/ANTI_CSRF_TOKEN_HEADER_NAME)) presence)
      (-> request :headers (get constants/ANTI_CSRF_TOKEN_HEADER_NAME nil))
      (-> request :form-params (get (keyword constants/ANTI_CSRF_TOKEN_FORM_PARAM_NAME)))
      (throw (ex-info "The x-csrf-token has not been send!" {:status 403}))))

(defn anti-csrf-token [request]
  (or (:anti-csrf-token request)
      (-> request
          :cookies
          (get constants/ANTI_CSRF_TOKEN_COOKIE_NAME nil)
          :value
          presence)))

(defn anti-csrf-props [request]
  {:csrfToken {:value (anti-csrf-token request), :name constants/ANTI_CSRF_TOKEN_FORM_PARAM_NAME}})

(defn wrap [handler]
  (fn [request]
    (let [anti-csrf-token (anti-csrf-token request)]
      (when (and (http-unsafe? request) (not (token? request)))
        (when-not (presence anti-csrf-token)
          (throw (ex-info "The anti-csrf-token cookie value is not set." {:status 403})))
        (when-not (= anti-csrf-token (x-csrf-token! request))
          (throw (ex-info (str "The x-csrf-token is not equal to the "
                               "anti-csrf cookie value.") {:status 403}))))
      (let [anti-csrf-token-new (when-not (presence anti-csrf-token)
                                  (str (UUID/randomUUID)))]
        (-> request
            (cond-> anti-csrf-token-new
              (assoc :anti-csrf-token anti-csrf-token-new))
            handler
            (cond-> anti-csrf-token-new
              (assoc-in [:cookies constants/ANTI_CSRF_TOKEN_COOKIE_NAME]
                        {:value anti-csrf-token-new
                         :http-only false
                         :path "/"
                         :secure false})))))))

;#### debug ###################################################################
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
