(ns leihs.core.env)

(defn use-global-navbar? []
  (= (.-globalNavbar document.body.dataset) "true"))
