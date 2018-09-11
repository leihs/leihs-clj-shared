(ns leihs.core.url.core
  (:refer-clojure :exclude [str keyword encode decode])
  (:require
    [leihs.core.url.shared :as shared]
    ))

(def encode shared/encode)
(def decode shared/decode)
