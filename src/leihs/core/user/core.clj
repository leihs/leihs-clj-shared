(ns leihs.core.user.core
  (:require [clojure.tools.logging :as log]))

(defn wrap-me-id 
  ([handler]
   (fn [request]
     (wrap-me-id handler request)))
  ([handler request]
   (handler
     (if (= "me" (-> request :route-params :user-id))
       (assoc-in request [:route-params :user-id]
                 (-> request :authenticated-entity :user_id))
       request))))

