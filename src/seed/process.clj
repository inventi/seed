(ns seed.process
  (require [automat.core :as a]
           [seed.command :as command]
           [seed.event-bus :as eb]
           [seed.process-repo :as prepo]
           [seed.util :refer [keywordize-name camel->lisp]]
           [clojure.core.async :as async :refer [go <! >! go-loop]]
           [clojure.tools.logging :as log]))

(defn process-id [e]
  (get-in e [:metadata :process-id]))

(defn- advance [state machine event]
  (a/advance machine state event ))

(defn- dispatch-command [state event event-store]
  (let [{:keys [stream-id] :as cmd} (get-in state [:value :command])]
    (command/handle-cmd {} stream-id cmd
                        {:process-id (get-in event [:metadata :process-id])}
                        event-store)))

(defn- transition [machine
                   {:keys [metadata event-type] :as event}
                   process-repo]
  (->
    (let [state (prepo/load-state-with-retry! (process-id event) process-repo)]
      (if (empty? state)
        (assoc state
               :event event
               :trigger-event event)
        (update-in state [:value] assoc :event event)))
    (advance machine (keyword (camel->lisp event-type)))
    (prepo/save-state-with-retry! (process-id event) process-repo)))

(defn failed-event [error metadata]
  {:event-type "CommandFailed" :metadata metadata :data {:cause (:error error)}})

(defn step-process [process {:keys [metadata] :as event}
                    {:keys [process-repo event-store] :as system}]
  (go
    (let [state (transition process event process-repo)]
      (if (:accepted? state)
        state
        (let [[events error] (<! (dispatch-command state event event-store))]
          (if error
            (step-process process (failed-event error metadata) system)
            state))))))

(defn- start-process [events-ch process accounts-cmp]
  (go-loop []
           (when-some [event (<! events-ch)]
                      (->
                        (<! (step-process process event accounts-cmp))
                        :accepted?
                        (if
                          (async/close! events-ch)
                          (recur)))))
  events-ch)

(defn trigger [process event-class {:keys [event-bus] :as accounts}]
  (let [event-type (.getSimpleName event-class)
        ch (eb/subscribe-by :event-type event-type event-bus)]
    (go-loop []
             (when-some [event (<! ch)]
                        (let [id (get-in event [:data :id])]
                          (log/info "triggering process id" id)
                          (-> (eb/subscribe-by process-id id event-bus)
                              (start-process process accounts)
                              (async/>! (update-in event [:metadata] assoc :process-id id))))
                        (recur))))
  accounts)

