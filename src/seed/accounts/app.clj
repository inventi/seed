(ns seed.accounts.app
 (require [com.stuartsierra.component :as component]
          [seed.core.event-store :as event-store]
          [seed.core.event-bus :as event-bus]
          [seed.accounts.transfer :as transfer]
          [seed.core.process :as process]
          [seed.core.process-repo :as process-repo]
          [seed.core.command :as command]
          [seed.accounts.account :as account]))

(defrecord Accounts []
  component/Lifecycle

  (start [{:keys [event-bus event-store process-repo] :as component}]
    (let [transfer-loop (partial
                          process/fsm-loop transfer/transfer-process
                          event-store process-repo)]
      (process/trigger transfer-loop
                       seed.accounts.transfer.TransferInitiated
                       event-bus)
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

(defn debitaccount! [account amount {event-store :event-store}]
  (command/handle-cmd account (account/->DebitAccount account amount "EUR") event-store))

(defn creditaccount! [account amount {event-store :event-store}]
  (command/handle-cmd account (account/->CreditAccount account amount "EUR") event-store))

(defn openaccount! [party {event-store :event-store}]
  (let [number (str (java.util.UUID/randomUUID))]
    {:chan (command/handle-cmd number (account/->OpenAccount number "EUR" party party) event-store)
     :number number}))

(defn transfer-money [from to amount {:keys [event-store]}]
  (let [id (str (java.util.UUID/randomUUID))]
    (command/handle-cmd id (transfer/->InitiateTransfer id from to amount) event-store)))
