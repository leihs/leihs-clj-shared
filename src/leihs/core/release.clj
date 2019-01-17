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
  (->> [:version_major :version_minor :version_patch]
       (select-keys latest)
       vals
       (map str)
       (clojure.string/join ".")))
