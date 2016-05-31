(ns seed.core.aggregate
  (require [seed.core.event-store :as es]
           [clojure.core.async :as async :refer [go-loop chan close! >! <! go]]
           [seed.core.util :refer [camel->lisp get-namespace new-empty-event success error]]
           [clojure.tools.logging :as log]))

(defprotocol Aggregate
    (state  [event state]))

(defn current-state [init-state events]
  (reduce #(state %2 %1) init-state (reverse events)))

(defn seed-event [event-ns {:keys [event-type data]}]
  (into (new-empty-event (str event-ns "." event-type)) data))

(defn load-state! [init-state id aggregate-ns event-store]
  (go-loop [state init-state
            version (:version init-state)]
           (let [stream (str aggregate-ns "-" id)
                 event-num (if (nil? version) 0 (inc version))
                 [events err :as result] (<!(es/load-events stream event-num event-store))]
             (if err
               result
               (if (empty? events)
                 (success (assoc state :version version))
                 (recur
                   (->> events
                        (map (partial seed-event aggregate-ns))
                        (current-state state))
                   (:event-number (first events))))))))

(defn es-event [metadata event]
  (assoc {}
         :data (into {} event)
         :event-type (.getSimpleName (type event))
         :metadata metadata))

(defn save-events! [events metadata version id aggregate-ns event-store]
  (es/save-events
    (map (partial es-event metadata) events)
    (str aggregate-ns "-" id)
    version  event-store))

