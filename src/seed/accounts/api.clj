(ns seed.accounts.api
  (:require  [compojure.core :refer [defroutes context GET POST]]
            [compojure.route :as route]
            [clojure.core.async :refer [<!!]]
            [seed.core.command :as command]
            [seed.core.event-store :as es]
            [seed.core.aggregate :as aggregate]
            [seed.accounts.account :as account]
            [seed.accounts.transfer :as transfer]
            [seed.accounts.pm.smtransfer :as smtransfer]
            [ring.util.response :refer  [response]]))

(defn openaccount!
  ([party]
   (openaccount! party nil))

  ([party limit]
   (let [number (str (java.util.UUID/randomUUID))]
     (<!! (command/handle-cmd
            (assoc
              (account/->OpenAccount number "EUR" party party limit)
              ::command/stream-id number)))
     number)))

(defn state [id stream-ns]
  (let [[state err]
        (<!! (aggregate/load-state! {} id stream-ns))]
    state))

(defn account-state [number]
  (state number `seed.accounts.account))

(defn transfer-state [id]
  (state id `seed.accounts.transfer))

(defn transfer-money [from to amount]
  (let [id (str (java.util.UUID/randomUUID))]
    (command/handle-cmd {}
                        (assoc (transfer/->InitiateTransfer id from to amount)
                               ::command/stream-id id)
                        {::es/correlation-id id})
    id))

(defroutes routes
  (context "/accounts" request
           (POST "/" {{:keys [holder]} :body}
                 (response (assoc {} :number (openaccount! holder))))

           (GET "/:id" {{:keys [id]} :params}
                (response (account-state id))))
  (context "/transfers" request
           (POST "/" {{:keys [from to amount]} :body}
                 (response (assoc {} :id (transfer-money from to amount))))
           (GET "/:id" {{:keys [id]} :params}
                (response (transfer-state id))))

  (route/not-found  "<h1>Page not found</h1>"))

(defn test-accounts []
  (let [acc1 (openaccount! "g1")
        acc2 (openaccount! "g2" 500)]
    (command/handle-cmd (assoc (account/->DebitAccount acc1 800 "EUR")
                               ::command/stream-id acc1))
    [acc1 acc2]))
