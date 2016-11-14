(ns seed.core.event-store
  (:require [mount.core :refer  [defstate]]
           [seed.core.config :refer [config]]
           [clojure.core.async :as async :refer [chan close! >!! >! <! go go-loop]]
           [clojure.data.json :as json]
           [seed.core.util :refer [keywordize-name keywordize-exception error]])
  (:import [akka.actor ActorSystem]
           [akka.pattern Patterns]
           [eventstore.tcp ConnectionActor]
           [eventstore SubscriptionObserver]
           [eventstore.j
            SettingsBuilder EsConnectionFactory EventDataBuilder
            WriteEventsBuilder ReadStreamEventsBuilder SubscribeToBuilder]
           [seed.core.eventstore.j DelegatingActor MsgReceiver DelegatingOnSuccess]
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


(defstate actor-system
  :start (ActorSystem/create)
  :stop (.shutdown actor-system))

(defstate actor-con
  :start (start-connection! actor-system (build-settings (:event-store config))))

(defstate es-con
  :start (EsConnectionFactory/create actor-system (build-settings (:event-store config))))

(defn terminated? [actor]
  (.isTerminated actor))

(defn msg-receiver [chan]
  (DelegatingOnSuccess.
    (reify MsgReceiver
      (onInit [this _])
      (onReceive [this msg]
        (async/put! chan msg)))))

(defn- send! [action]
  (when (terminated? actor-con)
    (throw (IllegalStateException. "No connection to event store!")))
  (let [chan (chan)]
    (doto (Patterns/ask actor-con action 1000)
      (.onSuccess (msg-receiver chan) (.dispatcher actor-system))
      (.onFailure (msg-receiver chan) (.dispatcher actor-system)))
    chan))

(defn event->record [{:keys [event-type data metadata] :as event}]
  (.build
    (doto
      (EventDataBuilder. event-type)
      (.eventId (java.util.UUID/randomUUID))
      (.jsonData (json/write-str data))
      (.jsonMetadata (json/write-str metadata)))))

(defn- as-json [data]
  (json/read
    (java.io.InputStreamReader.
      (java.io.ByteArrayInputStream.
        (.. data value toArray)))
    :key-fn keyword))

(defrecord Event [event-type data metadata event-number])

(defn record->event [record]
  (map->Event
    {:event-type (.. record data eventType)
     :data (as-json (.. record data data))
     :metadata (as-json (.. record data metadata))
     :event-number (.. record number value)}))

(defn indexed->event [event]
  (assoc (record->event (.event event))
         :position (->Position
                     (.. event position commitPosition)
                     (.. event position preparePosition))))

(defn write-stream [stream events version]
  (.build
    (let [builder (WriteEventsBuilder. stream)]
      (if (nil? version)
        (.expectAnyVersion builder)
        (.expectVersion builder version))
      (doseq [event events]
        (.addEvent builder (event->record event)))
      builder)))

(defn read-stream [stream from-num]
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

(defn- keywordize-error
  ([msg]
   (when (instance? Exception msg)
     (exception->error msg))))

(defn- with-version [{:keys [error] :as e} expected-version]
  (if (= error :wrong-expected-version)
    (assoc e :expected-version expected-version)
    e))

(defn- write-events-to-stream [events stream expected-version]
  (go
    (->
       stream
       (write-stream events expected-version)
       send!
       <!
       keywordize-error
       (with-version expected-version)
       error)))

(defn stream [stream-ns id]
  (str stream-ns "-" id))

(defn- get-records [result]
  (when result
    (.eventsJava result)))

(defn- read-events-from-stream [stream from-event-num]
  (let [result-chan (-> stream
                        (read-stream from-event-num)
                        send!)]
    (go
      (let [result (async/<! result-chan)
            err (keywordize-error result)]
        (if err
          (condp = (:error err)
            :stream-not-found [`() nil]
            (error err))
          [(reverse (map record->event (get-records result))) nil])))))

(defn save-events [events stream-id expected-version]
  (write-events-to-stream events stream-id expected-version))

(defn load-events
  [stream-id from-event-num]
  (read-events-from-stream stream-id from-event-num))

(defn system-event? [event]
  (->
    (.. event event data eventType)
    (.startsWith  "$")))

(defn live-event-stream []
  (let [events-chan (chan)]
    (.subscribeToAll
      es-con
      (reify SubscriptionObserver
        (onLiveProcessingStart [this subscription])
        (onEvent [this event subscription]
          (when-not (system-event? event)
            (when-not (async/put! events-chan (indexed->event event))
              (.close subscription))))
        (onError [this e])
        (onClose [this]
          (close! events-chan))) false nil)
    events-chan))
