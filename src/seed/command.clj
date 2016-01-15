(ns seed.command
  (require [seed.event-store :as es]
           [clojure.core.async :as async :refer [go-loop chan close! >! <! go]]
           [seed.util :refer [camel->lisp get-namespace new-empty-event success error]]
           [clojure.tools.logging :as log]))

(defrecord CommandError [error])

(defprotocol CommandHandler
    (perform  [command state]))

(defn cmd-error [e]
  (error (->CommandError e)))

(defn seed-event [event-ns {:keys [event-type data]}]
  (into (new-empty-event (str event-ns "." event-type)) data))

(defn es-event [event]
  (assoc {:data (into {} event)}
         :event-type (.getSimpleName (type event))))

(defn es-events [events metadata]
  (map #(assoc (es-event %)
               :metadata metadata) events))

(defn state-fn [event-ns]
  (ns-resolve event-ns (symbol "state")))

(defn next-state [event-ns state event]
  (if-let [apply-event (state-fn event-ns)]
    (apply-event event state)
    (throw (IllegalStateException. (str "event " (.getName (type event)) " doesn't exist")))))

(defn current-state [event-ns state events]
  (reduce (partial next-state event-ns) state (reverse events)))

(defn from-event [version]
  (if (nil? version) 0 (inc version)))

(defn load-stream-state! [init-state id stream-ns event-store]
  (go-loop [state init-state
            version (:version init-state)]
           (let [stream (str stream-ns "-" id)
                 [events err :as result] (<!(es/load-events stream (from-event version) event-store))]
             (if err
               result
               (if (empty? events)
                 (success (assoc state :version version))
                 (recur
                   (->> events
                        (map (partial seed-event stream-ns))
                        (current-state stream-ns state))
                   (:event-number (first events))))))))

(defn run-cmd [state id command metadata event-store]
  (go
    (let [[state e :as result]  (<!(load-stream-state! state id (get-namespace command) event-store))
          [events e :as result] (if (nil? e) (perform command state) result)
          [_ e :as result]      (if (nil? e)
                                  (<!(es/save-events
                                       (es-events events metadata)
                                       (str (get-namespace command) "-" id)
                                       (:version state) event-store))
                                  result)]
      {:loaded-state state
       :events events
       :error e})))

(defn run-cmd-with-retry [init-state id command metadata event-store]
  (go-loop [state init-state
            retries 0]
           (let [{:keys [loaded-state events error] :as result}
                 (<!(run-cmd state id command metadata event-store))]
             (if (and (= (:error error) :wrong-expected-version)
                      (> 100 retries))
               (do
                 (log/info "retrying due to wrong version. Retries" retries)
                 (recur loaded-state (inc retries)))
               result))))

(defn handle-cmd
  ([id command event-store]
   (handle-cmd {} id command event-store))

  ([init-state id command event-store]
   (handle-cmd init-state id command {} event-store))

  ([init-state id command metadata event-store]
   (run-cmd-with-retry init-state id command metadata event-store)))

