(ns leihs.core.time-zone-test
  (:require
   [clojure.test :refer :all]
   [java-time :as t]
   [leihs.core.time-zone :as tz]))

(deftest zone-id-test
  (is (= (t/zone-id "Europe/Zurich") (tz/zone-id "Bern")))
  (is (= (t/zone-id "Europe/Zurich") (tz/zone-id "Europe/Zurich")))
  (is (= (t/zone-id "UTC") (tz/zone-id "No/Such_Zone")))
  (is (= (t/zone-id "UTC") (tz/zone-id nil))))

(deftest today-in-test
  (let [instant (t/instant "2026-10-07T22:30:00Z")]
    (is (= (t/local-date "2026-10-08") (tz/today-in "Bern" instant)))
    (is (= (t/local-date "2026-10-07") (tz/today-in "UTC" instant)))))
