(ns seed.core.process
  (require [automat.core :as a]
           [seed.core.command :as command]
           [seed.core.manage-process :as manage-process]
           [seed.core.event-bus :as eb]
           [seed.core.util :refer [keywordize-name camel->lisp]]
           [clojure.core.async :as async :refer [go <! >! go-loop]]
           [clojure.tools.logging :as log]))

(defn- dispatch-command [cmd process-id]
  (command/handle-cmd {} (:stream-id cmd) cmd {:process-id process-id}))

(defn fsm-loop [fsm-pattern fsm-reducers]
  (log/info "compiling pattern")
  (let [fsm (a/compile [fsm-pattern] {:reducers fsm-reducers})]
    (log/info "pattern compiled, starting event loop")
    (fn [events-ch id trigger]
      (async/<!! (dispatch-command
        (manage-process/map->TriggerProcess
          {:id id :fsm fsm :stream-id id :trigger-event trigger})
        id))
      (go-loop []
               (when-some [event (<! events-ch)]
                 (condp = (:event-type event)
                   "ProcessStarted" (recur)
                   "StepProceeded" (recur)
                   "ProcessClosed" (async/close! events-ch)
                   (let [{:keys [events error]}
                         (<! (dispatch-command
                               (manage-process/map->StepProcess
                                 {:id id :fsm fsm :event event :stream-id id})
                               id))]
                     (recur))))))))

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

