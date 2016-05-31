(ns seed.core.process
  (require [automat.core :as a]
           [seed.core.command :as command]
           [seed.core.event-bus :as eb]
           [seed.core.process-repo :as prepo]
           [seed.core.util :refer [keywordize-name camel->lisp]]
           [clojure.core.async :as async :refer [go <! >! go-loop]]
           [clojure.tools.logging :as log]))

(defn- with-event [state event]
  (if (empty? state)
        (assoc state
               :event event
               :trigger-event event)
        (update-in state [:value] assoc :event event)))

(defn- next-state [state machine event]
  (let [input (keyword (camel->lisp (:event-type event)))]
    (a/advance machine (with-event state event) input)))

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
  (a/compile  [1 2 3]);automat hangs compiling transfer-pattern, dont know why. But doing this helps, will figure it out later
  (let [fsm (a/compile [fsm-pattern] {:reducers fsm-reducers})]
    (log/info "pattern compiled, starting event loop")
    (fn [events-ch id]
      (go-loop []
               (when-some [event (<! events-ch)]
                 (->
                   (<! (step-process id fsm event))
                   (if
                     (async/close! events-ch)
                     (recur)))))
      events-ch)))

(defn process-id  [e]
  (get-in e  [:metadata :process-id]))

(defn trigger [process-loop event-class]
  (let [event-type (.getSimpleName event-class)
        ch (eb/subscribe-by :event-type event-type)]
    (go-loop []
             (when-some [event (<! ch)]
                        (let [id (get-in event [:data :id])]
                          (log/info "triggering process id" id)
                          (-> (eb/subscribe-by process-id id)
                              (process-loop id)
                              (async/>! (update-in event [:metadata] assoc :process-id id))))
                        (recur)))))

