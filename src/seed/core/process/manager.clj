(ns seed.core.process.manager
  (:require [automat.core :as a]
           [seed.core.command :as command]
           [seed.core.aggregate :as aggregate]
           [seed.core.util :refer [camel->lisp success]]
           [clojure.core.async :as async :refer [<!!]]
           [clojure.tools.logging :as log]))

(defrecord TriggerProcess [])
(defrecord StepProcess [])
(defrecord CloseProcess [])

(defrecord ProcessStarted [])
(defrecord CommandFailed [cause])
(defrecord StepProceeded [])
(defrecord ProcessClosed [])

(defn- dispatch-command [cmd process-id]
  (command/handle-cmd {} cmd {:process-id process-id}))

(defn- with-event [state event]
  (if (:value state)
    (update-in state [:value] assoc :event event)
    (assoc state :trigger-event event :event event)))

(defn- input [event]
  (keyword (camel->lisp (:event-type event))))

(defn- next-state [state machine event]
  (a/advance machine (with-event state event) (input event)))

(defn step-process [state fsm id {:keys [metadata] :as event}]
  (let [state (next-state (:state state) fsm event)
        cmd (get-in state [:value :command])]
    (if-not (:accepted? state)
      (let [{:keys [error]} (<!!(dispatch-command cmd id))]
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
    (assoc state :state (:state event)))

  ProcessClosed
  (state [event state]
    state)

  CommandFailed
  (state [event state]
    state))
