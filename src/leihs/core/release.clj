(ns leihs.core.release
  (:require
   [leihs.core.core :refer [presence]]
   [taoensso.timbre :refer [debug error info spy warn]]))

(def DEV-VERSION "dev")
(def BETA-SUFFIX "-beta")
(def GH-URL "https://github.com/leihs/leihs")
(def GH-RELEASES-URL (str GH-URL "/releases"))

(def version
  (or (presence (System/getenv "LEIHS_VERSION"))
      DEV-VERSION))

(def gh-link
  (if (or (= version DEV-VERSION)
          (clojure.string/ends-with? version BETA-SUFFIX))
    GH-URL
    (str GH-RELEASES-URL "/tag/" version)))
