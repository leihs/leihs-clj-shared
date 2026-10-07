(ns leihs.core.availability.changes-test
  (:require
   [clojure.test :refer :all]
   [java-time :as t]
   [leihs.core.availability.changes :as ch]))

(def weekdays-open
  {:monday true :tuesday true :wednesday true :thursday true :friday true
   :saturday false :sunday false :holidays []})

(def thursday (t/local-date "2026-10-08"))

(defn- maintained-until [pool period]
  (ch/being-maintained-until pool {:maintenance_period period} thursday))

(deftest next-open-date-test
  (is (= (t/local-date "2026-10-09")
         (ch/next-open-date weekdays-open (t/local-date "2026-10-09"))))
  (is (= (t/local-date "2026-10-12")
         (ch/next-open-date weekdays-open (t/local-date "2026-10-10"))))
  (let [all-closed (assoc (zipmap (keys weekdays-open) (repeat false))
                          :holidays [])]
    (is (= (t/local-date "2026-10-10")
           (ch/next-open-date all-closed (t/local-date "2026-10-10"))))))

(deftest being-maintained-until-test
  (is (= thursday (maintained-until weekdays-open 0)))
  (is (= (t/local-date "2026-10-09") (maintained-until weekdays-open 1)))
  (is (= (t/local-date "2026-10-12") (maintained-until weekdays-open 2)))
  (is (= (t/local-date "2026-10-14")
         (maintained-until (assoc weekdays-open
                                  :holidays [{:start_date "2026-10-12"
                                              :end_date "2026-10-13"}])
                           2))))

(deftest late?-test
  (let [overdue {:end_date (t/minus (ch/local-date) (t/days 1))
                 :returned_date nil}]
    (is (true? (ch/late? (assoc overdue :status "signed"))))
    (is (false? (ch/late? (assoc overdue :status "approved"))))
    (is (false? (ch/late? (assoc overdue
                                 :status "signed"
                                 :end_date (t/plus (ch/local-date)
                                                   (t/days 1))))))))

(deftest get-unavailable-until-test
  (let [reservation {:end_date (t/plus (ch/local-date) (t/days 10))
                     :returned_date nil}
        unloaded-pool (delay (throw (ex-info "pool loaded" {})))]
    (is (= (:end_date reservation)
           (ch/get-unavailable-until reservation
                                     {:maintenance_period nil}
                                     unloaded-pool)))
    (is (= (:end_date reservation)
           (ch/get-unavailable-until reservation
                                     {:maintenance_period 0}
                                     unloaded-pool)))))
