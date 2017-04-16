(ns seed.core.process
  (:require [seed.core.command :as command]
            [seed.core.event-bus :as eb]
            [seed.core.event-store :as es]
            [seed.core.util :refer [keywordize camel->lisp success]]
            [clojure.core.async :as async :refer [go <! >! go-loop]]
            [clojure.tools.logging :as log]))

(defn correlation-id [e]
  (get-in e [::es/correlation-id]))

(defn start-process [start-process-fn event]
  (go
    (let [id (::es/correlation-id event)
          events-ch (eb/subscribe-by correlation-id id)]
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

