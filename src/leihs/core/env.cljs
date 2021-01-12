(ns leihs.core.env)

(defn use-global-navbar? []
  (= (.-globalNavbar js/document.body.dataset) "true"))
