(ns seed.core.test.command
  (require [clojure.core.async :refer [go <!!]]
           [seed.core.util :refer [success error]]
           [seed.core.event-store :as es])
  (use [seed.core.command]
       [clojure.test]))

(defrecord DoSuccess [])
(defrecord DoError [])
(defrecord SuccessHappened [])
(defrecord NoHandler [])

(extend-protocol CommandHandler
  DoSuccess
  (perform [command state]
    (success [(->SuccessHappened)]))

  DoError
  (perform [command state]
    (cmd-error "command went wrong")))

(defprotocol TestAggregate
  (state [event state]))

(extend-protocol TestAggregate
  SuccessHappened
  (state [event state]
    (if (:status state)
      (assoc state :status :success2)
      (assoc state :status :success))))


(defn load-events-at-once [stream ver es]
  (go
    (if (zero? ver)
      (success [{:data {} :event-type "SuccessHappened" :event-number 3}
                {:data {} :event-type "SuccessHappened" :event-number 2}])
      (success []))))

(defn load-events-in-batches [stream ver es]
  (go
    (condp = ver
      0 (success [{:data {} :event-type "SuccessHappened" :event-number 2}])
      3 (success [{:data {} :event-type "SuccessHappened" :event-number 3}])
      (success []))))

(defn load-events-with-error [stream ver es]
  (go
    [nil (es/->EventStoreError :some-error "some error")]))

(defn save-events-with-success [events & args]
  (go (success events)))

(defn save-events-with-wrong-version [events & args]
  (go [nil (es/->EventStoreError :wrong-expected-version "")]))

(deftest test-next-state
  (testing "should throw exception on event with no handler"
    (is (= IllegalStateException
           (try
             (next-state `seed.command {} (->NoHandler))
             (catch IllegalStateException e
               (type e)))))
    (is (= IllegalArgumentException)
        (try
             (next-state *ns* {} (->NoHandler))
             (catch IllegalArgumentException e
               (type e)))))
  (testing "should update state"
    (is (= {:status :success}
           (next-state *ns* {} (->SuccessHappened))))))

(deftest test-current-state
  (testing "should apply all events"
    (is (= {:status :success2}
           (current-state *ns* {} [(->SuccessHappened) (->SuccessHappened)])))))

(deftest test-load-stream-state
  (testing "version nill on no events"
    (is (= [{:version nil} nil]
           (with-redefs [es/load-events (fn [& args] (go (success nil)))]
             (<!! (load-stream-state! {} "stream-id" *ns* nil))))))
  (testing
    (is (= [{:status :success2 :version 3} nil]
           (with-redefs [es/load-events load-events-at-once]
             (<!! (load-stream-state! {} "stream-id" *ns* nil))))))

  (testing "should load events in batches"
    (is (= [{:status :success2 :version 3} nil]
           (with-redefs [es/load-events load-events-in-batches]
             (<!! (load-stream-state! {} "stream-id" *ns* nil))))))

  (testing "should resume loading from given state"
    (is (= [{:status :success :version 3} nil]
           (with-redefs [es/load-events load-events-in-batches]
             (<!! (load-stream-state! {:version 2} "stream-id" *ns* nil))))))

  (testing "should return error"
    (is (= [nil (es/->EventStoreError :some-error "some error")]
           (with-redefs [es/load-events load-events-with-error]
             (<!! (load-stream-state! {} "stream-id" *ns* nil)))))))

(deftest test-run-command
  (is (= {:events [(->SuccessHappened)]
          :loaded-state {:state "test" :status :success2 :version 3}
          :error nil}
         (with-redefs [es/load-events load-events-in-batches
                       es/save-events save-events-with-success]
           (<!! (run-cmd {:state "test"} "event-id" (->DoSuccess) {} nil)))))

  (testing "should return event store error if load fails"
    (is (= {:events nil
            :loaded-state nil
            :error (es/->EventStoreError :some-error  "some error") }
           (with-redefs [es/load-events load-events-with-error]
             (<!! (run-cmd {} "event-id" (->DoSuccess) {} nil))))))

  (testing "should return state and command error if command fails"
    (is (= {:events nil
            :loaded-state {:status :success2 :version 3}
            :error (->CommandError "command went wrong")}
           (with-redefs [es/load-events load-events-in-batches]
             (<!! (run-cmd {} "event-id" (->DoError) {} nil))))))

  (testing "should return state, events and write error if write fails"
    (is (= {:events [(->SuccessHappened)]
            :loaded-state {:status :success2 :version 3}
            :error (es/->EventStoreError :wrong-expected-version  "")}
           (with-redefs [es/load-events load-events-in-batches
                         es/save-events save-events-with-wrong-version]
             (<!! (run-cmd {} "event-id" (->DoSuccess) {} nil)))))))








