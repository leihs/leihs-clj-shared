(ns  leihs.core.url.http-test
  (:require
   [clojure.test :refer :all]
   [leihs.core.url.http :refer [parse-base-url]]))

(deftest parse-base-url-test []
  (is (= (parse-base-url "http://localhost:1234/ctx?enabled=yes")
         {:context "/ctx",
          :enabled true,
          :host "localhost",
          :port 1234,
          :protocol "http",
          :url "http://localhost:1234/ctx?enabled=yes"})))
