(ns seed.accounts.api
  (:require  [compojure.core :refer [defroutes context GET POST]]
            [compojure.route :as route]
            [clojure.core.async :refer [<!!]]
            [seed.core.command :as command]
            [seed.core.aggregate :as aggregate]
            [seed.accounts.account :as account]
            [seed.accounts.transfer :as transfer]
            [ring.util.response :refer  [response]]))

(defn openaccount! [party]
  (let [number (str (java.util.UUID/randomUUID))]
    (<!! (command/handle-cmd number (account/->OpenAccount number "EUR" party party)))
    number))

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
    (command/handle-cmd id (transfer/->InitiateTransfer id from to amount))
    id))

(defroutes routes
  (context "/accounts" request
           (POST "/" { {:keys [currency holder]} :body
                       accounts :accounts}
                 (response (assoc {} :number (openaccount! holder accounts))))

           (GET "/:id" {{:keys [id]} :params
                        accounts :accounts}
                (response (account-state id accounts))))
  (context "/transfers" request
           (POST "/" {{:keys [from to amount]} :body
                      accounts :accounts}
                 (response (assoc {} :id (transfer-money from to amount accounts))))
           (GET "/:id" {{:keys [id]} :params
                        accounts :accounts}
                (response (transfer-state id accounts))))

  (route/not-found  "<h1>Page not found</h1>"))

(defn test-accounts []
  (let [acc1 (openaccount! "g1")
        acc2 (openaccount! "g2")]
    (command/handle-cmd acc1 (account/->DebitAccount acc1 800 "EUR"))
    [acc1 acc2]))
