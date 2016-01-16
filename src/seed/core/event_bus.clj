(ns seed.core.event-bus
  (require [com.stuartsierra.component :as component]
           [clojure.core.async :as async :refer [chan mult]]
           [seed.core.event-store :as es]))

(defrecord Pub [chan pub])

(defn start-publisher! [event-store publish-chan]
  (async/pipe
    (es/subscribe->live-events! event-store)
    publish-chan))

(defrecord EventBus []
  component/Lifecycle

  (start [{:keys [event-store] :as component}]
    (let [publish-chan (chan)
          mult (mult publish-chan)]
      (start-publisher! event-store publish-chan)
      (assoc component
             :publish-chan publish-chan
             :publish-mult mult
             :pubs (atom {}))))

  (stop [{:keys [publish-chan pubs] :as component}]
    (if publish-chan
      (do
        (async/close! publish-chan)
        (assoc component
               :publish-chan nil))
      component)))

(defn new-event-bus []
 (->EventBus))

(defn- new-pub [f]
  (let [ch (chan)
        pub (async/pub ch f)]
    (->Pub ch pub)))

(defn- get-pub [{:keys [pubs publish-mult]} f]
  (if-some [pub (get @pubs f)]
           (:pub pub)
           (let [{:keys [chan pub] :as newpub} (new-pub f)]
             (async/tap publish-mult chan)
             (swap! pubs assoc f newpub)
             pub)))

(defn subscribe-by [f topic event-bus]
  (let [ch (chan)]
    (async/sub
      (get-pub event-bus f) topic ch)
    ch))

