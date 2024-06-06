(ns leihs.core.redirects
  (:require
   [leihs.core.paths :refer [path]]
   [leihs.core.user.permissions :as user-perms]
   [leihs.core.user.permissions.procure :as procure-perms]))

(defn redirect-target
  "Decides here to redirect a user from a root page based on access right, mostly after a login.
   Used as a last resort if no explicit target (like a return-to parameter) is present."
  [tx user]
  (cond (user-perms/sysadmin? tx user) (path :admin)
        (:is_admin user) (path :admin)
        (user-perms/manager? tx user) (path :manage)
        (user-perms/borrow-access? tx user) (path :borrow)
        (procure-perms/any-access? tx user) (path :procurement)
        :else (path :auth-info)))
