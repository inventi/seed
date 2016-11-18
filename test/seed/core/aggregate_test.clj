(ns seed.core.aggregate-test
  (:require [clojure.core.async :refer [go <!!]]
            [seed.core.util :refer [success error]]
            [seed.core.event-store :as es]
            [seed.core.aggregate :as aggr])
  (:use [seed.core.aggregate]
        [clojure.test]))

(defrecord SuccessHappened [])

(extend-protocol Aggregate
  SuccessHappened
  (state [event state]
    (if (:status state)
      (assoc state :status :success2)
      (assoc state :status :success))))

(defn load-events-at-once [stream ver]
  (go
    (if (zero? ver)
      (success [{:data {} :event-type "SuccessHappened" :event-number 3}
                {:data {} :event-type "SuccessHappened" :event-number 2}])
      (success []))))

(defn load-events-in-batches [stream ver]
  (go
    (condp = ver
      0 (success [{:data {} :event-type "SuccessHappened" :event-number 2}])
      3 (success [{:data {} :event-type "SuccessHappened" :event-number 3}])
      (success []))))

(defn load-events-with-error [stream ver]
  (go
    [nil (es/->EventStoreError :some-error "some error")]))

(deftest test-current-state
  (testing "should apply all events"
    (is (= {:status :success2}
           (current-state {} [(->SuccessHappened) (->SuccessHappened)])))))

(deftest test-load-stream-state
  (testing "version nill on no events"
    (is (= [{::aggr/version nil} nil]
           (with-redefs [es/load-events (fn [& args] (go (success nil)))]
             (<!! (load-state! {} "stream-id" *ns*))))))
  (testing
    (is (= [{:status :success2 ::aggr/version 3} nil]
           (with-redefs [es/load-events load-events-at-once]
             (<!! (load-state! {} "stream-id" *ns*))))))

  (testing "should load events in batches"
    (is (= [{:status :success2 ::aggr/version 3} nil]
           (with-redefs [es/load-events load-events-in-batches]
             (<!! (load-state! {} "stream-id" *ns*))))))

  (testing "should resume loading from given state"
    (is (= [{:status :success ::aggr/version 3} nil]
           (with-redefs [es/load-events load-events-in-batches]
             (<!! (load-state! {::aggr/version 2} "stream-id" *ns*))))))

  (testing "should return error"
    (is (= [nil (es/->EventStoreError :some-error "some error")]
           (with-redefs [es/load-events load-events-with-error]
             (<!! (load-state! {} "stream-id" *ns*)))))))

