(ns leihs.core.release
  (:require
    [clojure.tools.logging :as log]
    [clj-yaml.core :as yaml]
    ))

(def file-content
  (try
    (-> (System/getProperty "user.dir")
        (str "/../config/releases.yml")
        slurp yaml/parse-string)
    (catch Exception ex
      (log/warn "Failed to read releases.yml, returning bogus value ")
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
