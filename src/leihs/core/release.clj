(ns leihs.core.release
  (:require
   [leihs.core.core :refer [presence]]
   [taoensso.timbre :refer [debug error info spy warn]]))

(def VERSION-FILE-NAME "LEIHS-VERSION")
(def DEV-VERSION "dev")
(def BETA-SUFFIX "-beta")
(def GH-URL "https://github.com/leihs/leihs")
(def GH-RELEASES-URL (str GH-URL "/releases"))

(def file-content
  (some-> VERSION-FILE-NAME
          clojure.java.io/resource
          slurp
          clojure.string/split-lines
          first))

(def version
  (or (presence file-content) DEV-VERSION))

(def gh-link
  (if (or (= version DEV-VERSION)
          (clojure.string/ends-with? version BETA-SUFFIX))
    GH-URL
    (str GH-RELEASES-URL "/tag/" version)))
