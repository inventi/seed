(ns seed.coffe.customer
  (:require [seed.core.command :as command :refer [cmd-error]]
            [seed.core.aggregate :as aggregate]
            [seed.core.util :refer [success]]))

(defrecord ConfirmPurchase [merchant])
(defrecord PurchaseConfirmed [])

(extend-protocol aggregate/Aggregate
  PurchaseConfirmed
  (state [event state]
    (assoc state
           :state :confirmed
           :merchant (:merchant event))))

(extend-protocol command/CommandHandler
  ConfirmPurchase
  (perform [command state]
    (success [(map->PurchaseConfirmed command)])))
