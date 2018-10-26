(ns leihs.core.env)

(defn use-remote-navbar? []
  (.-remoteNavbar document.body.dataset))
