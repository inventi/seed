(ns seed.core.process
  (require [automat.core :as a]
           [seed.core.command :as command]
           [seed.core.event-bus :as eb]
           [seed.core.process-repo :as prepo]
           [seed.core.util :refer [keywordize-name camel->lisp]]
           [clojure.core.async :as async :refer [go <! >! go-loop]]
           [clojure.tools.logging :as log]))

(defn- with-event [state event]
  (if (:value state)
    (update-in state [:value] assoc :event event)
    (assoc state :trigger-event event :event event)))

(defn- input [event]
  (keyword (camel->lisp (:event-type event))))

(defn- next-state [state machine event]
  (a/advance machine (with-event state event) (input event)))

(defn- transition [id machine event]
  (->
    (prepo/load-state! id)
    (next-state machine event)
    (prepo/save-state! id)))

(defn failed-event [error metadata]
  {:event-type "CommandFailed" :metadata metadata :data {:cause (:error error)}})

(defn- dispatch-command [cmd process-id]
  (command/handle-cmd {} (:stream-id cmd) cmd {:process-id process-id}))

(defn step-process [id fsm {:keys [metadata] :as event}]
  (go
    (let [state (transition id fsm event)
          cmd (get-in state [:value :command])]
      (when-not (:accepted? state)
        (let [{:keys [error]} (<!(dispatch-command cmd id))]
          (if error
            (step-process id fsm (failed-event error metadata)))))
      (:accepted? state))))

(defn fsm-loop [fsm-pattern fsm-reducers]
  (log/info "compiling pattern")
  (let [fsm (a/compile [fsm-pattern] {:reducers fsm-reducers})]
    (log/info "pattern compiled, starting event loop")
    (fn [events-ch id]
      (go-loop []
               (when-some [event (<! events-ch)]
                 (->
                   (<! (step-process id fsm event))
                   (if
                     (async/close! events-ch)
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
                 (process-fn events-ch id)
                 (async/>! events-ch (update-in event [:metadata] assoc :process-id id)))
               (recur)))))

