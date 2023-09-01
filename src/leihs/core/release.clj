(ns leihs.core.release
  (:require
    [clj-yaml.core :as yaml]
    [taoensso.timbre :refer [debug error info spy warn]]
    ))

(def file-content
  (try
    (-> (System/getProperty "user.dir")
        (str "/../config/releases.yml")
        slurp yaml/parse-string)
    (catch Exception ex
      (warn "Failed to read releases.yml, returning bogus value ")
      {:releases []}
      )))

(def latest
  (-> file-content
      :releases
      first))

(def version
  (let [v (->> [:version_major :version_minor :version_patch]
               (select-keys latest)
               vals
               (map str)
               (clojure.string/join "."))
        v-pre (:version_pre latest)]
    (if v-pre
      (clojure.string/join "-" [v v-pre])
      v)))
