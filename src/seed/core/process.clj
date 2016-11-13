(ns seed.core.process
  (require [automat.core :as a]
           [seed.core.command :as command]
           [seed.core.process.manager :refer [map->TriggerProcess map->StepProcess]]
           [seed.core.event-bus :as eb]
           [clojure.core.async :as async :refer [go <! >! go-loop]]
           [clojure.tools.logging :as log]))

(defn- dispatch-command [{:keys [stream-id] :as cmd}]
  (go
    (let [{:keys [error]}
          (<!(command/handle-cmd {} stream-id cmd {:process-id stream-id}))]
      (when error (log/error "Failed to send command to the process" cmd error)))))

(defn- fsm-event-loop
  "Wrap event in to command and send it to the process manager.
  Handle events and commands in signle thread only, as command will create new events
  which may end with race condition."
  [fsm events-ch id trigger]
  (async/<!!
    (dispatch-command
      (map->TriggerProcess {:id id :fsm fsm :stream-id id :trigger-event trigger})))
  (go-loop
    []
    (when-some [event (<! events-ch)]
      (condp = (:event-type event)
        "ProcessStarted" (recur)
        "StepProceeded" (recur)
        "ProcessClosed" (async/close! events-ch)
        (do
          (<!(dispatch-command
               (map->StepProcess {:id id :fsm fsm :event event :stream-id id})))
          (recur))))))

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
                 (log/debug "triggering process" event  "id" id)
                 (process-fn events-ch id
                             (update-in event [:metadata] assoc :process-id id)))
               (recur)))))

