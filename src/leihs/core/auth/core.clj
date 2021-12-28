(ns leihs.core.auth.core
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.auth.session :as session]
    [leihs.core.auth.token :as token]
    [leihs.core.core :refer [str keyword presence presence!]]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


;;; authentication ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn wrap-authenticate [handler]
  (-> handler
      token/wrap-authenticate
      session/wrap-authenticate
      ))



;;; authorization helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def HTTP-SAFE-VERBS #{:get :head :options :trace})

(defn http-safe? [request]
  (boolean (some-> request :request-method HTTP-SAFE-VERBS)))

(def HTTP-UNSAFE-VERBS #{:post :put :delete :patch})

(defn http-unsafe?  [request]
  (boolean (some-> request :request-method HTTP-UNSAFE-VERBS)))

(defn filter-required-scopes-wrt-safe-or-unsafe [request required-scopes]
  (if (http-safe? request)
    (filter (fn [[k v]]
              (#{:scope_read :scope_system_admin_read :scope_admin_read} k))
            required-scopes)
    required-scopes))


;;; authorizers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn scope-authorizer [required-scopes request]
  (let [required-scope-keys (->> required-scopes
                                 (filter (fn [[k v]] v))
                                 (filter-required-scopes-wrt-safe-or-unsafe
                                   request)
                                 (map first)
                                 set)]
    (if (every? (fn [scope-key]
                  (-> request :authenticated-entity scope-key))
                required-scope-keys)
      true
      (let [k (some (fn [scope-key]
                      (-> request :authenticated-entity scope-key not))
                    required-scope-keys)]
        false))))

(defn build-scope-authorizer [required-scopes]
  (fn [request]
    (scope-authorizer required-scopes request)))

(def admin-scopes?
  (build-scope-authorizer
    {:scope_admin_read true
     :scope_admin_write true
     :scope_system_admin_read false
     :scope_system_admin_write false}))

(def system-admin-scopes?
  (build-scope-authorizer
    {:scope_admin_read false
     :scope_admin_write false
     :scope_system_admin_read true
     :scope_system_admin_write true}))


;;; authorization ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn authorize! [request handler resolve-table]
  (let [handler-key (:handler-key request)
        authorizers (some-> resolve-table
                            (get handler-key)
                            :authorizers)]
    (when (nil? authorizers)
      (throw (ex-info (str "No authorizers for the handler " handler-key " are defined! "
                           "This is most likely a programming error.")
                      {:status 555})))
    (if (some
          (fn [authorizer] (-> request authorizer))
          authorizers)
      (handler request)
      (throw (ex-info "Not authorized" {:status 403})))))

(defn wrap [handler resolve-table]
  (fn [request]
    (authorize! request handler resolve-table)))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
