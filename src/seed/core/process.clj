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

(defn- transition [id machine event process-repo]
  (->
      (prepo/load-state-with-retry! id process-repo)
      (next-state machine event)
      (prepo/save-state-with-retry! id process-repo)))

(defn failed-event [error metadata]
  {:event-type "CommandFailed" :metadata metadata :data {:cause (:error error)}})

(defn- dispatch-command [cmd process-id event-store]
  (command/handle-cmd {} (:stream-id cmd) cmd {:process-id process-id} event-store))


(defn step-process [id process {:keys [metadata] :as event}
                    {:keys [process-repo event-store] :as system}]
  (go
    (let [state (transition id process event process-repo)
          cmd (get-in state [:value :command])]
      (if (:accepted? state)
        true
        (let [{:keys [error]} (<!(dispatch-command cmd id event-store))]
          (if error
            (step-process id process (failed-event error metadata) system)
            false))))))

(defn- start-process [events-ch id process accounts-cmp]
  (go-loop []
           (when-some [event (<! events-ch)]
                      (->
                        (<! (step-process id process event accounts-cmp))
                        (if
                          (async/close! events-ch)
                          (recur)))))
  events-ch)

(defn process-id  [e]
  (get-in e  [:metadata :process-id]))

(defn trigger [process event-class {:keys [event-bus] :as accounts}]
  (let [event-type (.getSimpleName event-class)
        ch (eb/subscribe-by :event-type event-type event-bus)]
    (go-loop []
             (when-some [event (<! ch)]
                        (let [id (get-in event [:data :id])]
                          (log/info "triggering process id" id)
                          (-> (eb/subscribe-by process-id id event-bus)
                              (start-process id process accounts)
                              (async/>! (update-in event [:metadata] assoc :process-id id))))
                        (recur))))
  accounts)

