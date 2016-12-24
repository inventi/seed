(ns seed.core.process.manager
  (:require [automat.core :as a]
           [seed.core.command :as command]
           [seed.core.aggregate :as aggregate]
           [seed.core.event-store :as es]
           [seed.core.util :refer [camel->lisp success keywordize-name]]
           [clojure.core.async :as async :refer [<!!]]
           [clojure.tools.logging :as log]
           [clojure.spec :as s]
           [clojure.spec.test :as stest]))

(defrecord TriggerProcess [])
(defrecord StepProcess [])
(defrecord CloseProcess [])

(defrecord ProcessStarted [])
(defrecord CommandFailed [cause])
(defrecord StepProceeded [])
(defrecord ProcessClosed [])

(defn drop-ns [m]
  (zipmap
    (map #(-> % name keyword) (keys m))
    (vals m)))

(defn- with-event [state event]
  (let [event (drop-ns event)]
    (if (:value state)
      (update-in state [:value] assoc :event event)
      (assoc state :trigger-event event :event event))))

(defn- input [{:keys [::es/event-type]}]
  (keyword (camel->lisp event-type)))

(defn- next-state [state machine event]
  (a/advance machine (with-event state event) (input event)))

(defn- with-cmd-type [state cmd]
  (update-in state [:value :command] assoc :type (keywordize-name cmd)))

(defn step-process [state fsm id event]
  (let [state (next-state (:state state) fsm event)
        cmd (get-in state [:value :command])]
    (if-not (:accepted? state)
      (let [{:keys [::command/error]} (<!! (command/handle-cmd {} cmd {:process-id id}))
            state (with-cmd-type state cmd)]
        (if error
          [(map->StepProceeded {:state state})
           (map->CommandFailed {:cause (:error error)})]
          [(map->StepProceeded {:state state})]))
      [(map->ProcessClosed {:state state})])))

(extend-protocol command/CommandHandler
  TriggerProcess
  (perform [{:keys [trigger-event fsm id]} state]
    (success
      (conj (seq (step-process state fsm id trigger-event))
            (map->ProcessStarted {:trigger-event trigger-event}))))

  StepProcess
  (perform [{:keys [event fsm id]} state]
   (success (step-process state fsm id event)))

  CloseProcess
  (perform [{:keys [amount currency] :as command}
            {:keys [balance] :as state}]))

(extend-protocol aggregate/Aggregate

  ProcessStarted
  (state [event state]
    state)

  StepProceeded
  (state [event state]
    (assoc state :state
           (update-in (:state event) [:value] dissoc :command)))

  ProcessClosed
  (state [event state]
    state)

  CommandFailed
  (state [event state]
    state))

(s/fdef step-process
        :args (s/cat :state map?
                     :fsm some?
                     :id string?
                     :event ::es/event))

(stest/instrument `step-process)
