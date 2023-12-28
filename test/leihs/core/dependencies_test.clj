(ns leihs.core.dependencies-test
  (:require
    [clojure.test :refer :all]
    [leihs.core.auth.core2]
    [leihs.core.auth.core]
    [leihs.core.core]
    [leihs.core.db :as db]
    [leihs.core.graphql.helpers]
    [leihs.core.url.http]
    [leihs.core.json-protocol]
    [leihs.core.paths]
    [leihs.core.url.core]
    [leihs.core.redirects]
    [leihs.core.ring-audits]
    [leihs.core.ring-exception]
    [leihs.core.routing.dispatch-content-type]
    [leihs.core.http-server]
    [leihs.core.shutdown]
    [leihs.core.sign-in.back]
    [leihs.core.sign-in.external-authentication.back]
    [leihs.core.sign-out.back]
    [leihs.core.sql]
    [leihs.core.ssr-engine]
    [leihs.core.ssr]
    ))

(deftest requiring-core-namespaces-with-dependencies-works []
 (is true))
