(ns seed.accounts.app
 (require [com.stuartsierra.component :as component]
          [seed.core.event-store :as event-store]
          [seed.core.event-bus :as event-bus]
          [seed.accounts.transfer :as transfer]
          [seed.core.process :as process]
          [seed.core.process-repo :as process-repo]))

(defrecord Accounts []
  component/Lifecycle

  (start [{:keys [event-store] :as component}]
    (process/trigger
      transfer/transfer-process seed.accounts.transfer.TransferInitiated
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
