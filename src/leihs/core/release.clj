(ns leihs.core.release
  (:require
   [clj-yaml.core :as yaml]
   [clojure.java.io :as io]
   [leihs.core.core :refer [presence]]
   [taoensso.timbre :refer [debug error info spy warn]]))

(def DEV-VERSION "dev")
(def GH-URL "https://github.com/leihs/leihs")
(def GH-RELEASES-URL (str GH-URL "/releases"))

(def version
  (or (presence (System/getenv "LEIHS_VERSION"))
      DEV-VERSION))

(def gh-link
  (if (= version DEV-VERSION)
    GH-URL
    (str GH-RELEASES-URL "/tag/" version)))

(def built-info
  (some-> "built-info.yml" io/resource slurp yaml/parse-string))

(def super-commit-id
  (:super_commit_id built-info))

(def display-version
  (if (and (= version DEV-VERSION) super-commit-id)
    (subs super-commit-id 0 7)
    version))

(def display-gh-link
  (if (and (= version DEV-VERSION) super-commit-id)
    (str GH-URL "/tree/" super-commit-id)
    gh-link))
