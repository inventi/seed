(ns seed.core.command
  (:require [seed.core.aggregate :as aggregate]
           [clojure.core.async :as async :refer [go-loop chan close! >! <! go]]
           [seed.core.util :refer [get-namespace error]]
           [clojure.tools.logging :as log]
           [clojure.spec :as s]
           [clojure.spec.test :as stest]))

(defrecord CommandError [error])

(defprotocol CommandHandler
    (perform  [command state]))

(defn cmd-error [e]
  (error (->CommandError e)))

(defn run-cmd [state {:keys [::stream-id] :as command} metadata]
  (go
    (let [aggregate-ns (get-namespace command)
          [state err :as result]  (<!(aggregate/load-state! state stream-id  aggregate-ns))
          [events err :as result] (if (nil? err) (perform command state) result)
          [_ err :as result]      (if (nil? err)
                                    (<!(aggregate/save-events!
                                         events metadata
                                         (::aggregate/version state) stream-id aggregate-ns))
                                    result)]
      {::loaded-state state
       ::events events
       ::error err})))

(defn run-cmd-with-retry [init-state command metadata]
  (go-loop [state init-state
            retries 0]
           (let [{:keys [::loaded-state ::events ::error] :as result}
                 (<!(run-cmd state command metadata))]
             (if (and (= (:error error) :wrong-expected-version)
                      (> 100 retries))
               (do
                 (log/info "retrying due to wrong version. Retries" retries "err:" error)
                 (recur loaded-state (inc retries)))
               result))))

(defn handle-cmd
  ([command]
   (handle-cmd {} command))

  ([init-state command]
   (handle-cmd init-state command {}))

  ([init-state command metadata]
   (run-cmd-with-retry init-state command metadata)))

(s/def ::stream-id string?)
(s/def ::command (s/keys :req [::stream-id]))

(s/fdef handle-cmd
        :args (s/cat :state (s/? map?)
                     :cmd ::command
                     :meta (s/? map?)))

(stest/instrument `handle-cmd)

