(ns leihs.core.auth.core
  (:refer-clojure :exclude [str keyword])
  (:require
   [leihs.core.core :refer [keyword str presence]]
   [leihs.core.routing.front :as routing]
   [leihs.core.user.front :as current-user]))

; this is a misnomer since "Authorization" on the frontent is per se pointless,
; however it "feels and looks" like authorization on the backen
; usecases are mostly UX driven i.e. disabling or hiding links which would result in a 403 etc
;
; required information for an authorizer is the current-user and the routing information; the
; latter can be used to gives access to certain inventory-pools e.g.

; so an authorizer always takes two arguments: the current-user-state and the routing-state
; it responds with true or false

;;; helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn allowed? [authorizers]
  (some
   (fn [authorizer]
     (authorizer @current-user/state* @routing/state*))
   authorizers))

;;; basic authorizers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn all-granted [_ _]
  true)

;;; admin scope authorizers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn scope-authorizer [required-scopes user-state _routing-state]
  (let [required-scope-keys (->> required-scopes
                                 (filter (fn [[k v]] v))
                                 (map first)
                                 set)]
    (if (every? (fn [scope-key] (get user-state scope-key)) required-scope-keys)
      true
      false)))

(defn admin-scopes? [user-state _routing-state]
  (scope-authorizer
   {:scope_admin_read true
    :scope_admin_write true
    :scope_system_admin_read false
    :scope_system_admin_write false}
   user-state _routing-state))

(defn system-admin-scopes? [user-state _routing-state]
  (scope-authorizer
   {:scope_admin_read false
    :scope_admin_write false
    :scope_system_admin_read true
    :scope_system_admin_write true}
   user-state _routing-state))

(defn current-user-admin-scopes? []
  (if @current-user/state*
    (admin-scopes? @current-user/state* nil)
    false))

(defn current-user-system-admin-scopes? []
  (if @current-user/state*
    (system-admin-scopes? @current-user/state* nil)
    false))


