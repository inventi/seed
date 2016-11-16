(ns seed.core.process
  (:require [automat.core :as a]
            [seed.core.command :as command]
            [seed.core.process.manager :refer [map->TriggerProcess map->StepProcess]]
            [seed.core.event-bus :as eb]
            [clojure.core.async :as async :refer [go <! >! go-loop]]
            [clojure.tools.logging :as log]))

(defn- fsm-event-loop
  "Wrap event in to command and send it to the process manager.
  Handle events and commands in signle thread only, as command will create new events
  which may end with race condition."
  [fsm events-ch id trigger]
  (let [cmd (map->TriggerProcess {:id id :fsm fsm ::command/stream-id id :trigger-event trigger})
        {:keys [::command/error]} (async/<!! (command/handle-cmd {} cmd {:process-id id}))]
    (when error
      (log/error "Failed to send command to the process" error cmd)
      (throw (java.lang.IllegalStateException. "Cant send command to the mannager" error))))
  (go-loop
    []
    (when-some [event (<! events-ch)]
      (condp = (:event-type event)
        "ProcessStarted" (recur)
        "StepProceeded" (recur)
        "ProcessClosed" (async/close! events-ch)
        (let [cmd (map->StepProcess {:id id :fsm fsm :event event ::command/stream-id id})
              {:keys [::command/error]} (<! (command/handle-cmd {} cmd {:process-id id}))]
          (if error
            (do
              (log/error "Failed to send command to the manager. Stopping loop." error cmd)
              (async/close! events-ch))
            (recur)))))))

(defn fsm-loop [fsm-pattern fsm-reducers]
  (log/info "compiling pattern")
  (let [fsm (a/compile [fsm-pattern] {:reducers fsm-reducers})]
    (log/info "pattern compiled, starting event loop")
    (partial fsm-event-loop fsm)))

(defn process-id [e]
  (get-in e [:metadata :process-id]))

(defn trigger [process-fn event-class]
  (let [trigger-ch (eb/subscribe-by
                     :event-type (.getSimpleName event-class))]
    (go-loop []
             (when-some [event (<! trigger-ch)]
               (let [id (get-in event [:data :id])
                     events-ch (eb/subscribe-by process-id id)]
                 (log/debug "triggering process" event "id" id)
                 (process-fn events-ch id event))
               (recur)))))

