(ns leihs.core.json
  (:require
   #?(:clj [cheshire.core])
   #?(:clj [leihs.core.json-protocol])
   [clojure.walk]))

(defn to-json [d]
  #?(:clj (cheshire.core/generate-string d)
     :cljs (js/JSON.stringify d)))

(defn from-json [s]
  (clojure.walk/keywordize-keys
   #?(:clj (cheshire.core/parse-string s)
      :cljs (-> s js/JSON.parse js->clj))))

(defn try-parse-json [x]
  (try
    (from-json x)
    (catch #?(:cljs js/Object
              :clj Exception) _
      x)))
