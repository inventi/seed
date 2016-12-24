(ns seed.core.process
  (:require [automat.core :as a]
            [seed.core.command :as command]
            [seed.core.process.manager :refer [map->TriggerProcess map->StepProcess]]
            [seed.core.event-bus :as eb]
            [seed.core.event-store :as es]
            [seed.core.util :refer [keywordize]]
            [clojure.core.async :as async :refer [go <! >! go-loop]]
            [clojure.tools.logging :as log]))

(defn- loop-fsm [fsm events-ch id]
  (go-loop []
           (when-some [event (<! events-ch)]
             (condp = (keywordize (::es/event-type event))
               :process-started (recur)
               :step-proceeded (recur)
               :process-closed (async/close! events-ch)
               (let [cmd (map->StepProcess {:id id :fsm fsm :event event ::command/stream-id id})
                     {:keys [::command/error] :as r} (<! (command/handle-cmd {} cmd {:process-id id}))]
                 (if error
                   (do
                     (log/error "Failed to send command to the manager. Stopping loop." error cmd)
                     (async/close! events-ch))
                   (recur)))))))

(defn- run-event-loop
  "Wrap event in to command and send it to the process manager.
  Handle events and commands in signle thread only, as command will create new events
  which may end with race condition."
  [fsm events-ch id trigger]
  (let [cmd (map->TriggerProcess {:id id :fsm fsm ::command/stream-id id :trigger-event trigger})
        {:keys [::command/error]} (async/<!! (command/handle-cmd {} cmd {:process-id id}))]
    (if error
      (log/error "Failed to send command to the process" error cmd)
      (loop-fsm fsm events-ch id))))

(defn fsm-loop [fsm-pattern fsm-reducers]
  (log/info "compiling pattern")
  (let [fsm (a/compile [fsm-pattern] {:reducers fsm-reducers})]
    (log/info "pattern compiled, starting event loop")
    (partial run-event-loop fsm)))

(defn process-id [e]
  (get-in e [::es/metadata :process-id]))

(defn trigger [start-process-fn trigger-event-class]
  (let [trigger-ch (eb/subscribe-by
                     ::es/event-type (.getSimpleName trigger-event-class))]
    (go-loop []
             (when-some [event (<! trigger-ch)]
               (let [id (get-in event [::es/data :id])
                     events-ch (eb/subscribe-by process-id id)]
                 (log/debug "triggering process" event "id" id)
                 (if-let [process (start-process-fn events-ch id event)]
                   (do
                     (log/info "process started" id)
                     (log/info "process ended" id (<! process)))
                   (log/error "failed to start process with event" event "id" id)))
               (recur)))))

