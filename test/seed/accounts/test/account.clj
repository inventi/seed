(ns seed.accounts.test.account
  (:use [seed.accounts.account]
        [clojure.test])
  (:require [seed.core.command :as command :refer [perform cmd-error]]
            [seed.core.util :refer [success]]
            [seed.core.aggregate :as aggregate]))

(defn with-state [& events]
  (aggregate/current-state {} events))

(deftest test-credit
  (testing "should credit account"
    (is
      (=
       (perform (->CreditAccount nil 100 "EUR")
                (with-state
                  (->AccountDebited 100 "EUR")
                  (map->AccountOpened {})))
       (success [(->AccountCredited 100 "EUR")]))))

  (testing "cant credit empty account"
    (is
      (=
       (perform (->CreditAccount nil 100 "EUR")
                (with-state (map->AccountOpened {})))
       (cmd-error :insuficient-balance))))

  (testing "do not allow to credit on not existing account"
    (is
      (=
       (perform (->CreditAccount nil 100 "EUR") {})
       (cmd-error :account-doesnt-exist)))))

(deftest test-debit
  (testing "should debit account"
    (is
      (=
       (perform (->DebitAccount nil 100 "EUR")
                (with-state (map->AccountOpened {})))
       (success [(->AccountDebited 100 "EUR")]))))

  (testing "do not allow to debit on not existing account"
    (is
      (=
       (perform (->DebitAccount nil 100 "EUR") {})
       (cmd-error :account-doesnt-exist)))))

