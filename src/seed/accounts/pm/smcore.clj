(ns seed.accounts.pm.smcore
  (:require [automat.core :as a]
            [seed.core.command :as command]
            [seed.core.event-bus :as eb]
            [seed.core.event-store :as es]
            [seed.core.util :refer [keywordize camel->lisp success]]
            [clojure.core.async :as async :refer [go <! >! go-loop]]
            [clojure.tools.logging :as log]))

(defrecord FailCommand [cause])
(defrecord CommandFailed [])

(extend-protocol command/CommandHandler
  FailCommand
  (perform [command state]
   (success [(map->CommandFailed command)])))

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


(defn failed-event [error metadata]
  (merge
    {::es/event-type "CommandFailed" ::es/data {:cause (:error error)}}
    metadata))

(defn- step
  ([fsm event]
   (step fsm nil event))
  ([fsm state event]
   (go
     (let [new-state (next-state fsm state event)]
       (if-not (:accepted? new-state)
         (let [{:keys [::command/error]} (<! (command/handle-cmd {} (get-in new-state [:value :command])
                                                                 {::es/correlation-id (::es/correlation-id event)
                                                                  ::es/causation-id (::es/id event)}))]
           (when error
             (<! (command/handle-cmd {} (assoc
                                          (->FailCommand error)
                                          ::command/stream-id (::es/correlation-id event))
                                     {::es/correlation-id (::es/correlation-id event)
                                      ::es/causation-id (::es/id event)})))
           new-state)
         nil)))))

(defn- loop-fsm [fsm events-ch trigger]
  (go-loop [state (<! (step fsm trigger))]
           (when-some [event (<! events-ch)]
             (if-let [st (<! (step fsm state event))]
               (recur st)
               (async/close! events-ch)))))

(defn fsm-loop [fsm-pattern fsm-reducers]
  (log/info "compiling pattern")
  (let [fsm (a/compile [fsm-pattern] {:reducers fsm-reducers})]
    (log/info "pattern compiled, starting event loop")
    (partial loop-fsm fsm)))

