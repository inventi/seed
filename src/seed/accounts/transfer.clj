(ns seed.accounts.transfer
  (:require [mount.core :refer [defstate]]
            [seed.core.command :as command]
            [seed.core.aggregate :as aggregate]
            [seed.accounts.account :as account]
            [seed.core.util :refer [success]]))

(defrecord InitiateTransfer [id from to amount])
(defrecord CompleteTransfer [])
(defrecord FailTransfer [])

(defrecord TransferInitiated [id from to amount])
(defrecord TransferCompleted [])
(defrecord TransferFailed [])

(extend-protocol aggregate/Aggregate
  TransferInitiated
  (state [event state]
    (assoc event
         :state :initiated))

  TransferCompleted
  (state [event state]
    (assoc state
           :state :completed))

  TransferFailed
  (state [event state]
    (assoc state
           :state :failed
           :cause (:cause event))))

(extend-protocol command/CommandHandler
  InitiateTransfer
  (perform [command state]
   (success [(map->TransferInitiated command)]))

  CompleteTransfer
  (perform [command state]
    (success [(map->TransferCompleted command)]))

  FailTransfer
  (perform [command state]
    (success [(map->TransferFailed command)])))

