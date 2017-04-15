(ns seed.coffe.merchant
  (:require [seed.core.command :as command :refer [cmd-error]]
            [seed.core.aggregate :as aggregate]
            [seed.core.util :refer [success]]))


(defrecord ConfirmPurchase [customer])
(defrecord PurchaseConfirmed [])

(defrecord SubmitTransaction [customer amount])
(defrecord AproveTransaction [customer customer-exist])
(defrecord RejectTransaction [])

(defrecord TransactionSubmited [])
(defrecord TransactionAproved [])
(defrecord TransactionRejected [])
