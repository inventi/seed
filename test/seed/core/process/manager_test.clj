(ns seed.core.process.manager-test
  (:require [clojure.core.async :as async :refer [go <!! >!! chan to-chan]]
            [automat.core :as a]
            [seed.core.command :as command :refer [perform]]
            [seed.core.aggregate :as aggregate]
            [seed.core.event-store :as es]
            [seed.core.util :refer [error success]])
  (:use [clojure.test]
        [seed.core.process.manager]))

(defrecord MyCmd [])

(defn er-cmd [& args]
  (go {::command/error "simple error"}))

(defn ok-cmd [& args]
  (go {}))

(defn with-state [& events]
  (aggregate/current-state {} events))

(defn get-cmd [state input]
  (let [event (get-in state [:event :data])
        trigger (get-in state [:trigger-event :data])]
    (when (and event trigger)
      (assoc state :command (->MyCmd)))))

(def cmd (map->MyCmd {:type "my-cmd"}))

(def process [:a (a/$ :cmd)
              :b (a/$ :cmd)
              :c ])

(def fsm (a/compile process {:reducers {:cmd get-cmd}}))

(defn event [t]
  {::es/event-type t
   ::es/data {:my-data "data"}
   ::es/metadata {}})

(defn serialized-event [t]
  {:event-type t
   :data {:my-data "data"}
   :metadata {}})

(defn advance [fsm & events]
  (reduce (partial a/advance fsm) nil events))

(def a-events
  (conj
      []
      (map->ProcessStarted {:trigger-event (event "a")})
      (map->StepProceeded
        {:state
         (->
           (advance fsm :a)
           (assoc :value
                  {:trigger-event (serialized-event "a")
                   :event (serialized-event "a")
                   :command cmd}))})))
(def b-events
  (conj
    []
    (map->StepProceeded
      {:state
       (->
         (advance fsm :a :b)
         (assoc :value
                {:trigger-event (serialized-event "a")
                 :event (serialized-event "b")
                 :command cmd}))})))

(def c-events
  (conj
    []
    (map->ProcessClosed
      {:state
       (->
         (advance fsm :a :b :c)
         (assoc :value
                {:trigger-event (serialized-event "a")
                 :event (serialized-event "c")}))})))

(deftest test-manager
  (with-redefs [command/handle-cmd ok-cmd]
    (testing "should start the process"
      (is
        (= (success a-events)
           (perform (map->TriggerProcess
                      {:id "id" :fsm fsm :trigger-event (event "a")}) {}))))

    (testing "should step process"
      (is (= (success b-events)
             (perform (map->StepProcess
                        {:id "id" :fsm fsm :event (event "b")})
                      (apply with-state a-events)))))

    (testing "should close process"
      (is (= (success c-events)
             (perform (map->StepProcess
                        {:id "id" :fsm fsm :event (event "c")})
                      (apply with-state (concat b-events c-events))))))))

