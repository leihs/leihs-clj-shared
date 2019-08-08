(ns leihs.core.shared)

(defn head
  [& tags]
  [:head
   (conj tags
         [:meta {:charset "utf-8"}]
         [:meta
          {:name "viewport",
           :content "width=device-width, initial-scale=1, shrink-to-fit=no"}])])

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
