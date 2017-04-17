(ns seed.accounts.pm.smtransfer
  (:require [automat.core :as a]
            [mount.core :refer [defstate]]
            [seed.accounts.transfer :as transfer]
            [seed.accounts.pm.smcore :as smcore]
            [seed.core.command :as command]
            [seed.core.process :as process]
            [seed.core.aggregate :as aggregate]
            [seed.accounts.account :as account]
            [seed.core.util :refer [success]]))

(def pattern
  [(a/or [:transfer-initiated
          :command-failed (a/$ :fail-transfer)
          :transfer-failed]

         [:transfer-initiated
          :account-credited
          :command-failed (a/$ :reverse-credit)
          :account-debited (a/$ :fail-transfer)
          :transfer-failed]

         [:transfer-initiated (a/$ :credit-from-account)
          :account-credited (a/$ :debit-to-account)
          :account-debited (a/$ :complete-transfer)
          :transfer-completed])])

(defn- credit-from-account [{{{:keys [from amount]} :data} :trigger-event :as state} input]
  (->>
    {:number from :amount amount :currency "EUR" ::command/stream-id from}
    account/map->CreditAccount
    (assoc state :command)))

(defn- debit-to-account [{{{:keys [to amount]} :data} :trigger-event :as state} input]
  (->>
    {:number to :amount amount :currency "EUR" ::command/stream-id to}
    account/map->DebitAccount
    (assoc state :command)))

(defn- reverse-credit [{{{:keys [from amount]} :data} :trigger-event
                        {{:keys [cause]} :data} :event :as state} input]
  (->>
    {:number from :amount amount :currency "EUR" ::command/stream-id from :cause cause}
    account/map->DebitAccount
    (assoc state :command)))

(defn- complete-transfer [{{{:keys [id]} :data} :trigger-event :as state} input]
  (->>
    {:process-id id ::command/stream-id id}
    transfer/map->CompleteTransfer
    (assoc state :command)))

(defn- fail-transfer [{{{:keys [cause]} :data :as event} :event
                       {{:keys [id]} :data} :trigger-event
                       :as state} input]
  (->>
    {:process-id id ::command/stream-id id :cause cause}
    transfer/map->FailTransfer
    (assoc state :command)))

(def reducers
  {:credit-from-account credit-from-account
   :debit-to-account debit-to-account
   :complete-transfer complete-transfer
   :reverse-credit reverse-credit
   :fail-transfer fail-transfer})

(defstate transfer-pm
  :start (process/trigger
           (smcore/fsm-loop pattern reducers) :transfer-initiated))
