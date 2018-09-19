(ns leihs.core.auth.core
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.core.core :refer [str keyword presence presence!]]
    [leihs.core.auth.session :as session]
    [leihs.core.auth.token :as token]

    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


(defn wrap-authenticate [handler]
  (-> handler
      token/wrap-authenticate
      session/wrap-authenticate
      ))
