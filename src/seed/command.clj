(ns seed.command
  (require [seed.event-store :as es]
           [clojure.core.async :as async :refer [go-loop chan close! >! <! go]]
           [seed.util :refer [camel->lisp get-namespace new-empty-event]]
           [clojure.tools.logging :as log]))

(defrecord CommandError [error])

(defprotocol CommandHandler
    (perform  [command state]))

(defn error [e]
  [nil (->CommandError e)])

(defn success [r]
  [r nil])

(defn resolve-event-func [t n]
  (ns-resolve (symbol n) (symbol (camel->lisp t))))

(defn data->event [{:keys [event-type data]} event-ns]
  (into (new-empty-event (str event-ns "." event-type)) data))

(defn event->data [event]
  (assoc {:data event}
         :event-type (.getSimpleName (type event))))

(defn apply-events [state events event-ns]
  (assoc
    (reduce
      (fn [state event]
        (let [apply-event (ns-resolve event-ns (symbol "update-state"))]
          (apply-event (data->event event event-ns) state)))
      state
      (reverse events))
    :version (:event-number (first events))))

(defn cmd [state command]
  (perform command state))

(defn next-event-num [state]
  (if-let [version (:version state)] (inc version) 0))

(defn load-stream-state! [initial-state id stream-ns event-store]
  (go-loop [state initial-state]
           (let [[events err] (<!(es/load-events (str stream-ns "-" id) (next-event-num state) event-store))]
             (if err
               (into (error {}) err)
               (if (empty? events)
                 (success state)
                 (recur (apply-events state events stream-ns)))))))

(defn with-retry-num [state]
  (if-let [retry (:retry state)]
    (assoc state :retry (inc retry))
    (assoc state :retry 0)))

(defn map-events->data [events metadata]
  (map #(assoc (event->data %)
               :metadata metadata) events))

(defn save-evts [events metadata stream version event-store]
  (es/save-events
    (map-events->data events metadata)
    stream
    version event-store))

(defn if-ok [[v e :as result] f]
  (if (nil? e)
        (f v)
        result))

(defn should-retry? [[_ e] state]
  (and
    (= (:error e) :wrong-expected-version)
    (> 10 (:retry state))))

(defn perform-command! [initial-state id command metadata event-store]
  (go
    (let [[state _ :as result] (<!(load-stream-state! initial-state id (get-namespace command) event-store))
          result (-> result
                     (if-ok #(perform command %))
                     (if-ok #(es/save-events
                               (map-events->data % metadata)
                               (str (get-namespace command) "-" id)
                               (:version state) event-store)))]
      (if (coll? result)
        result
        (let [result (<! result)]
          (if (should-retry? result state)
            (do
              (log/debug "retrying due to wrong version. Retries" (:retry state))
              (<! (perform-command! (with-retry-num state) id command metadata event-store)))
            result))))))

(defn handle-cmd
  ([id command event-store]
   (handle-cmd {:retry 0} id command event-store))

  ([initial-state id command event-store]
   (handle-cmd initial-state id command {} event-store))

  ([initial-state id command metadata event-store]
   (perform-command! initial-state id command metadata event-store)))

