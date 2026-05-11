(ns leihs.core.release
  (:require
   [leihs.core.core :refer [presence]]
   [taoensso.timbre :refer [debug error info spy warn]]))

(def DEV-VERSION "dev")
(def GH-URL "https://github.com/leihs/leihs")
(def GH-RELEASES-URL (str GH-URL "/releases"))

(def version
  (or (presence (System/getenv "LEIHS_VERSION"))
      DEV-VERSION))

(def gh-link
  (cond
    (= version DEV-VERSION) GH-URL
    (re-matches #"[0-9a-f]+" version) (str GH-URL "/tree/" version)
    :else (str GH-RELEASES-URL "/tag/" version)))

(def display-version version)

(def display-gh-link gh-link)
