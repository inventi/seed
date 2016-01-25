(ns seed.accounts.account
  (require [automat.core :as a]
           [seed.core.command :as command :refer [cmd-error]]
           [seed.core.util :refer [success]]))

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
      (cmd-error :account-doesnt-exist)
      (success [(map->AccountDebited command)])))

  CreditAccount
  (perform [{:keys [amount] :as command}
            {:keys [balance] :as state}]
    (if-not (account-exist? state)
      (cmd-error :account-doesnt-exist)
      (if (< (- balance amount) 0)
        (cmd-error :insuficient-balance)
        (success [(map->AccountCredited command)])))))

(defprotocol Account
  (state[event state]))

(extend-protocol Account

  AccountOpened
  (state [event state]
    (assoc state
           :state :created
           :number (:account-number event)
           :balance 0))

  AccountDebited
  (state [event state]
    (assoc state
           :balance (+ (:balance state) (:amount event))))

  AccountCredited
  (state [event state]
    (assoc state
           :balance (- (:balance state) (:amount event)))))


