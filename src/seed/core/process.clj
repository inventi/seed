(ns seed.core.process
  (:require [automat.core :as a]
            [seed.core.command :as command]
            [seed.core.event-bus :as eb]
            [seed.core.event-store :as es]
            [seed.core.util :refer [keywordize camel->lisp]]
            [clojure.core.async :as async :refer [go <! >! go-loop]]
            [clojure.tools.logging :as log]))

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

(defn next-state [fsm state event]
  (a/advance fsm (with-event state event) (input event)))

(defn- step
  ([fsm event]
   (step fsm nil event))
  ([fsm state event]
   (let [new-state (next-state fsm state event)]
     (if-not (:accepted? new-state)
       (do
         (command/handle-cmd {} (get-in new-state [:value :command])
                             {::es/correlation-id (::es/correlation-id event)
                              ::es/causation-id (::es/id event)})
         ;have to do something with command failure here
         new-state)
       nil))))

(defn- loop-fsm [fsm events-ch trigger]
  (go-loop [state (step fsm trigger)]
           (when-some [event (<! events-ch)]
             (if-let [st (step fsm state event)]
               (recur st)
               (async/close! events-ch)))))

(defn fsm-loop [fsm-pattern fsm-reducers]
  (log/info "compiling pattern")
  (let [fsm (a/compile [fsm-pattern] {:reducers fsm-reducers})]
    (log/info "pattern compiled, starting event loop")
    (partial loop-fsm fsm)))

(defn process-id [e]
  (get-in e [::es/correlation-id]))

(defn start-process [start-process-fn event]
  (go
    (let [id (::es/correlation-id event)
          events-ch (eb/subscribe-by process-id id)]
      (log/debug "triggering process" event "id" id)
      (if-let [process (start-process-fn events-ch event)]
        (do
          (log/info "process started" id)
          (log/info "process ended" id (<! process)))
        (log/error "failed to start process with event" event "id" id)))))

(defn trigger [start-process-fn trigger-event-class]
  (let [trigger-ch (eb/subscribe-by
                     ::es/event-type (.getSimpleName trigger-event-class))]
    (go-loop []
             (when-some [event (<! trigger-ch)]
               (start-process start-process-fn event)
               (recur)))))

