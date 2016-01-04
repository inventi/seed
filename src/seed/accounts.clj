(ns seed.accounts
 (require [com.stuartsierra.component :as component]
          [seed.event-store :as event-store]
          [seed.event-bus :as event-bus]
          [seed.transfer :as transfer]
          [seed.process :as process]
          [seed.process-repo :as process-repo]))

(defrecord Accounts []
  component/Lifecycle

  (start [{:keys [event-store] :as component}]
    (process/trigger
      transfer/transfer-process seed.transfer.TransferInitiated
      component))

  (stop [component]
    component))

(defn accounts-system []
  (component/system-map
    :event-store (event-store/new-event-store)
    :event-bus (component/using
                 (event-bus/new-event-bus)
                 [:event-store])
    :process-repo (process-repo/new-process-repo)
    :accounts (component/using
                (->Accounts)
                [:event-store :event-bus :process-repo])))
