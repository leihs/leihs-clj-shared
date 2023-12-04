(ns leihs.core.auth.core
  (:refer-clojure :exclude [str keyword])
  (:require
   [clojure.java.jdbc :as jdbc]
   [leihs.core.auth.session :as session]
   [leihs.core.auth.token :as token]
   [leihs.core.core :refer [str keyword presence presence!]]
   [leihs.core.sql :as sql]
   [logbug.catcher :as catcher]
   [logbug.debug :as debug]
   [taoensso.timbre :refer [error warn info debug spy]]))

;;; authentication ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn wrap-authenticate [handler]
  (-> handler
      token/wrap-authenticate
      session/wrap-authenticate))

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

(defn admin-hierarchy-user-query [user-id]
  (-> (sql/select :id :admin_protected :system_admin_protected)
      (sql/from :users)
      (sql/where [:= :users.id user-id])))

(comment (admin-hierarchy-user-query "foo"))

(defn admin-hierarchy?
  "Checks that the aut-entity has the proper admin scope
  with respect to the requirements :system_admin_protected
  or :admin_protected of the user referenced by the :user-id
  param in the request (respectively by the supplied user-id-fn function)."
  [{auth-entity :authenticated-entity
    tx :tx :as request}
   & {:keys [user-id-fn]
      :or {user-id-fn #(-> % :route-params :user-id)}}]
  (when-let [user-id (user-id-fn request)]
    (when-let [user (some-> user-id admin-hierarchy-user-query
                            spy sql/format spy (->> (jdbc/query tx) first))]
      (cond
        (http-safe?
         request) (if (:system_admin_protected user)
                    (:scope_system_admin_write auth-entity)
                    (:scope_admin_write auth-entity))
        (http-unsafe?
         request) (if (:system_admin_protected user)
                    (:scope_system_admin_read auth-entity)
                    (:scope_admin_read auth-entity))))))

;;; authorization ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn authorize! [request handler resolve-table]
  (let [handler-key (:handler-key request)
        authorizers (some-> resolve-table
                            (get handler-key)
                            :authorizers)]
    (debug {'authorizers authorizers
            'request request})
    (when (nil? handler-key)
      (throw (ex-info (str "No handler for the route " (:uri request))
                      {:status 544})))
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
    (debug 'wrap {'request request})
    (authorize! request handler resolve-table)))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
