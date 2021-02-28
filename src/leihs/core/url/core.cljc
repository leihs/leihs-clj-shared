(ns leihs.core.url.core
  (:refer-clojure :exclude [str keyword encode decode])
  (:require
    [leihs.core.url.shared :as shared]
    ))

(def encode shared/encode)
(def decode shared/decode)

(defn decode-keys [m]
  (if-not (map? m)
    m
    (->> m
         (map (fn [[k v]] [k (decode v)]))
         (into {}))))
