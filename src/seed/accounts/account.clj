(ns seed.accounts.account
  (require [automat.core :as a]
           [seed.core.command :as command :refer [cmd-error]]
           [seed.core.aggregate :as aggregate]
           [seed.core.util :refer [success]]))

(defrecord OpenAccount [number currency applicant holder])
(defrecord DebitAccount  [number amount currency])
(defrecord CreditAccount [number amount currency])

(defrecord AccountOpened [number currency applicant holder])
(defrecord AccountCredited [amount currency])
(defrecord AccountDebited [amount currency])

(extend-protocol aggregate/Aggregate

  AccountOpened
  (state [event state]
    (assoc state
           :state :created
           :number (:number event)
           :balance 0))

  AccountDebited
  (state [event state]
    (assoc state
           :balance (+ (:balance state) (:amount event))))

  AccountCredited
  (state [event state]
    (assoc state
           :balance (- (:balance state) (:amount event)))))


(defn account-exist? [state]
  (= (:state state) :created))

(extend-protocol command/CommandHandler
  OpenAccount
  (perform [command state]
    (success [(map->AccountOpened command)]))

  DebitAccount
  (perform [{:keys [amount currency]} state]
    (if-not (account-exist? state)
      (cmd-error :account-doesnt-exist)
      (success [(->AccountDebited amount currency)])))

  CreditAccount
  (perform [{:keys [amount currency] :as command}
            {:keys [balance] :as state}]
    (if-not (account-exist? state)
      (cmd-error :account-doesnt-exist)
      (if (< (- balance amount) 0)
        (cmd-error :insuficient-balance)
        (success [(->AccountCredited amount currency)])))))

