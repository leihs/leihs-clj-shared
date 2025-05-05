(ns leihs.core.resources.images.core)

(def IMG-DATA-URL-PREFIX "data:image/jpeg;base64")

(defn prefix-with-data-url [base64]
  (clojure.string/join "," [IMG-DATA-URL-PREFIX base64]))
