(ns seed.core.event-bus
  (:require [mount.core :refer  [defstate]]
           [clojure.core.async :as async :refer [chan mult]]
           [seed.core.event-store :as es]))

(defrecord Pub [chan pub])

(defn start-publisher! [publish-chan]
  (async/pipe (es/live-event-stream) publish-chan))

(defstate publish-chan
  :start (doto (chan) start-publisher!)
  :stop (async/close! publish-chan))

(defstate publish-mult
  :start (mult publish-chan))

(defstate pubs
  :start (atom {}))

(defn- new-pub [f]
  (let [ch (chan)
        pub (async/pub ch f)]
    (->Pub ch pub)))

(defn- get-pub [f]
  (if-some [pub (get @pubs f)]
           (:pub pub)
           (let [{:keys [chan pub] :as newpub} (new-pub f)]
             (async/tap publish-mult chan)
             (swap! pubs assoc f newpub)
             pub)))

(defn subscribe-by [f topic]
  (let [ch (chan)]
    (async/sub (get-pub f) topic ch)
    ch))

