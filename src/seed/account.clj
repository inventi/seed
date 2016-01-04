(ns seed.account
  (require [automat.core :as a]
           [seed.command :as command :refer [success error]]))

(defrecord OpenAccount [number currency applicant holder])
(defrecord DebitAccount  [account-number amount currency])
(defrecord CreditAccount [account-number amount currency])

(defrecord AccountOpened [account-number currency applicant holder])
(defrecord AccountCredited [amount currency])
(defrecord AccountDebited [amount currency])

(defn account-exist? [state]
  (= (:state state) :created))

(extend-protocol command/CommandHandler
  OpenAccount
  (perform [command state]
    (success [(apply ->AccountOpened (vals command))]))

  DebitAccount
  (perform [command state]
    (if-not (account-exist? state)
      (error :account-doesnt-exist)
      (success [(map->AccountDebited command)])))

  CreditAccount
  (perform [command state]
    (if-not (account-exist? state)
      (error :account-doesnt-exist)
      (if (<= (:balance state) 0)
        (error :insuficient-balance)
        (success [(map->AccountCredited command)])))))

(defprotocol Account
  (update-state [event state]))

(extend-protocol Account

  AccountOpened
  (update-state [event state]
    (assoc state
           :state :created
           :number (:account-number event)
           :balance 0))

  AccountDebited
  (update-state [event state]
    (assoc state
           :balance (+ (:balance state) (:amount event))))

  AccountCredited
  (update-state [event state]
    (assoc state
           :balance (- (:balance state) (:amount event)))))

(defn debitaccount! [account amount {event-store :event-store}]
  (command/handle-cmd account (->DebitAccount account amount "EUR") event-store))

(defn creditaccount! [account amount {event-store :event-store}]
  (command/handle-cmd account (->CreditAccount account amount "EUR") event-store))

(defn openaccount! [party {event-store :event-store}]
  (let [number (str (java.util.UUID/randomUUID))]
    {:chan (command/handle-cmd number (->OpenAccount number "EUR" party party) event-store)
     :number number}))

