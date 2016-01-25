(ns seed.core.event-store
  (require [com.stuartsierra.component :as component]
           [clojure.core.async :as async :refer [chan close! >!! >! <! go go-loop]]
           [clojure.data.json :as json]
           [seed.core.util :refer [keywordize-name keywordize-exception]])
  (import [akka.actor ActorSystem]
          [eventstore.tcp ConnectionActor]
          [eventstore SubscriptionObserver]
          [eventstore.j
           SettingsBuilder EsConnectionFactory EventDataBuilder
           WriteEventsBuilder ReadStreamEventsBuilder SubscribeToBuilder]
          [seed.core.eventstore.j DelegatingActor MsgReceiver]
          [java.net InetSocketAddress]))

(defrecord Position [commit prepare])

(defn- build-settings [{:keys [host port user password]}]
  (.build
    (doto
      (SettingsBuilder.)
      (.address (InetSocketAddress. host port))
      (.defaultCredentials user password))))

(defn- start-connection! [system settings]
  (.actorOf system (ConnectionActor/getProps settings) "es-connection"))

(defrecord EventStore [host port user password]
  component/Lifecycle

  (start [component]
    (if-not (:actor-system component)
      (let [actor-system (ActorSystem/create)]
        (assoc component
               :actor-system actor-system
               :actor-con (start-connection! actor-system (build-settings component))
               :es-con (EsConnectionFactory/create actor-system (build-settings component))))
      component))

  (stop [component]
    (if-let [actor-system (:actor-system component)]
      (do
        (.shutdown actor-system)
        (assoc component
               :actor-system nil
               :actor-con nil
               :es-con nil))
      component)))

(defn terminated? [actor]
  (.isTerminated actor))

(defn- new-result-actor [chan actor-system]
  (.actorOf
    actor-system
    (DelegatingActor/props (reify
                             MsgReceiver
                             (onInit [this actor])
                             (onReceive [this message]
                               (async/go
                                 (async/>! chan message)
                                 (close! chan)))))))

(defn- send! [action {:keys [actor-system actor-con]}]
  (when (terminated? actor-con)
    (throw (IllegalStateException. "No connection to event store!")))
  (let [chan (chan)]
    (.tell actor-con action (new-result-actor chan actor-system))
    chan))

(defn event->record [{:keys [event-type data metadata] :as event}]
  (.build
    (doto
      (EventDataBuilder. event-type)
      (.eventId (java.util.UUID/randomUUID))
      (.data (json/write-str data))
      (.metadata (json/write-str metadata)))))

(defn- as-json [data]
  (json/read
    (java.io.InputStreamReader.
      (java.io.ByteArrayInputStream.
        (.. data value toArray)))
    :key-fn keyword))

(defn record->event [record]
  (assoc {}
         :event-type (.. record data eventType)
         :data (as-json (.. record data data))
         :metadata (as-json (.. record data metadata))
         :event-number (.. record number value)))

(defn indexed->event [event]
  (assoc (record->event (.event event))
         :position (->Position
                     (.. event position commitPosition)
                     (.. event position preparePosition))))

(defn write-stream-msg [stream events version]
  (.build
    (let [builder (WriteEventsBuilder. stream)]
      (if (nil? version)
        (.expectAnyVersion builder)
        (.expectVersion builder version))
      (doseq [event events]
        (.addEvent builder (event->record event)))
      builder)))

(defn read-stream-msg [stream from-num]
  (.build (doto (ReadStreamEventsBuilder. stream)
            (.forward)
            (.fromNumber (eventstore.EventNumber$Exact. (if (nil? from-num) 0 from-num)))
            (.resolveLinkTos false)
            (.requireMaster false))))

(defn subscribe-to-all-msg []
  (doto (SubscribeToBuilder.)
    (.toAll)))

(defrecord EventStoreError [error message])

(defn- exception->error [e]
  (->EventStoreError (keywordize-exception e) (.getMessage e)))

(defn- error
  ([msg]
   (error msg nil))

  ([msg expected-version]
   (when (= (type msg) akka.actor.Status$Failure)
     (let [{:keys [error] :as cause} (exception->error (.cause msg))]
       (if (= error :wrong-expected-version)
         (assoc cause :expected-version expected-version)
         cause)))))

(defn- write-events-to-stream [events stream expected-version event-store]
  (go
    [nil
     (->
       (write-stream-msg stream events expected-version)
       (send! event-store)
       <!
       (error expected-version))]))

(defn stream [stream-ns id]
  (str stream-ns "-" id))

(defn- get-records [result]
  (when result
    (.eventsJava result)))

(defn- read-events-from-stream [stream from-event-num event-store]
  (let [result-chan (-> stream
                        (read-stream-msg from-event-num)
                        (send! event-store))]
    (async/go
      (let [result (async/<! result-chan)
            err (error result)]
        (if err
          (condp = (:error err)
            :stream-not-found [`() nil]
            [nil err])
          [(reverse (map record->event (get-records result))) nil])))))

(defn save-events [events stream-id expected-version event-store]
  (write-events-to-stream events stream-id expected-version event-store))

(defn load-events
  [stream-id from-event-num event-store]
  (read-events-from-stream stream-id from-event-num event-store))

(defn new-event-store []
  (->EventStore "192.168.99.100" 1113 "admin" "changeit"))

(defn system-event? [event]
  (->
    (.. event event data eventType)
    (.startsWith  "$")))

(defn subscribe->live-events! [{:keys [es-con] :as event-store}]
  (let [events-chan (chan)]
    (.subscribeToAll
      es-con
      (reify SubscriptionObserver
        (onLiveProcessingStart [this subscription])
        (onEvent [this event subscription]
          (when-not (system-event? event)
            (when-not (>!! events-chan (indexed->event event))
              (.close subscription))))
        (onError [this e])
        (onClose [this]
          (close! events-chan))) false nil)
    events-chan))
