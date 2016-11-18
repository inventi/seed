(ns seed.core.command-test
  (:require [clojure.core.async :refer [go <!!]]
            [seed.core.util :refer [success error]]
            [seed.core.event-store :as es]
            [seed.core.aggregate :as aggregate]
            [seed.core.command :as command])
  (:use [seed.core.command]
        [clojure.test]))

(defrecord DoSuccess [])
(defrecord DoError [])
(defrecord SuccessHappened [])

(extend-protocol CommandHandler
  DoSuccess
  (perform [command state]
    (success [(->SuccessHappened)]))

  DoError
  (perform [command state]
    (cmd-error "command went wrong")))

(defn save-events-with-success [events & args]
  (go (success events)))

(defn save-events-with-wrong-version [events & args]
  (go [nil (es/->EventStoreError :wrong-expected-version "")]))

(defn success-state [& args]
  (go (success {:status :success2 :version 3})))

(defn error-state [& args]
  (go
    [nil (es/->EventStoreError :some-error "some error")]))

(deftest test-run-command
  (is (= {::command/events [(->SuccessHappened)]
          ::command/loaded-state {:status :success2 :version 3}
          ::command/error nil}
         (with-redefs [aggregate/load-state! success-state
                       aggregate/save-events! save-events-with-success]
           (<!! (run-cmd {} (->DoSuccess) {})))))

  (testing "should return event store error if load fails"
    (is (= {::command/events nil
            ::command/loaded-state nil
            ::command/error (es/->EventStoreError :some-error  "some error") }
           (with-redefs [aggregate/load-state! error-state]
             (<!! (run-cmd {} (->DoSuccess) {}))))))

  (testing "should return state and command error if command fails"
    (is (= {::command/events nil
            ::command/loaded-state {:status :success2 :version 3}
            ::command/error (->CommandError "command went wrong")}
           (with-redefs [aggregate/load-state! success-state]
             (<!! (run-cmd {} (->DoError) {}))))))

  (testing "should return state, events and write error if write fails"
    (is (= {::command/events [(->SuccessHappened)]
            ::command/loaded-state {:status :success2 :version 3}
            ::command/error (es/->EventStoreError :wrong-expected-version  "")}
           (with-redefs [aggregate/load-state! success-state
                         aggregate/save-events! save-events-with-wrong-version]
             (<!! (run-cmd {} (->DoSuccess) {})))))))

