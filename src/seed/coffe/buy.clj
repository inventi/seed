(ns seed.coffe.buy
  (:require [automat.core :as a]
           [seed.core.process :as process]
           [seed.core.command :as command]
           [seed.core.aggregate :as aggregate]
           [seed.core.util :refer [success]]))

(defrecord WaitCustomerConfirm [])
(defrecord WaitVendorTransaction [])

(def pattern
  [(a/or [:vendor-transaction-submitted (a/$ :wait-customer-transaction)
          :customer-transaction-confirmed (a/$ :check-customer)]
         [:customer-transaction-submitted (a/$ :wait-vendor-transaction)
          :vendor-transaction-submitted (a/$ :check-customer)])

   (a/or
     [:customer-exist (a/$ :aprove-transaction)
      :transaction-aproved]
     [:customer-not-exist (a/$ :reject-transaction)
      :transaction-rejected])])


