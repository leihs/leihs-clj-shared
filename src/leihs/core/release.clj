(ns leihs.core.release
  (:require [clojure.tools.logging :as log]
            [yaml.core :as yaml]))

(def file-content
  (-> (System/getProperty "user.dir")
      (str "/../config/releases.yml")
      yaml/from-file))

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
