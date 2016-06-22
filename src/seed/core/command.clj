(ns seed.core.command
  (require [seed.core.aggregate :as aggregate]
           [clojure.core.async :as async :refer [go-loop chan close! >! <! go]]
           [seed.core.util :refer [camel->lisp get-namespace new-empty-event success error]]
           [clojure.tools.logging :as log]))

(defrecord CommandError [error])

(defprotocol CommandHandler
    (perform  [command state]))

(defn cmd-error [e]
  (error (->CommandError e)))

(defn run-cmd [state id command metadata]
  (go
    (let [aggregate-ns (get-namespace command)
          [state err :as result]  (<!(aggregate/load-state! state id  aggregate-ns))
          [events err :as result] (if (nil? err) (perform command state) result)
          [_ err :as result]      (if (nil? err) (<!(aggregate/save-events!
                                                      events metadata (:version state) id aggregate-ns)) result)]
      {:loaded-state state
       :events events
       :error err})))

(defn run-cmd-with-retry [init-state id command metadata]
  (go-loop [state init-state
            retries 0]
           (let [{:keys [loaded-state events error] :as result}
                 (<!(run-cmd state id command metadata))]
             (if (and (= (:error error) :wrong-expected-version)
                      (> 100 retries))
               (do
                 (log/info "retrying due to wrong version. Retries" retries "err:" error)
                 (recur loaded-state (inc retries)))
               result))))

(defn handle-cmd
  ([id command]
   (handle-cmd {} id command))

  ([init-state id command]
   (handle-cmd init-state id command {}))

  ([init-state id command metadata]
   (run-cmd-with-retry init-state id command metadata)))


