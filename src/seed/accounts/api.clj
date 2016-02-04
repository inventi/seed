(ns seed.accounts.api
  (:require  [compojure.core :refer [defroutes context GET POST]]
            [compojure.route :as route]
            [clojure.core.async :refer [<!!]]
            [seed.core.command :as command]
            [seed.core.aggregate :as aggregate]
            [seed.accounts.account :as account]
            [seed.accounts.transfer :as transfer]
            [ring.util.response :refer  [response]]))

(defn openaccount! [party {event-store :event-store}]
  (let [number (str (java.util.UUID/randomUUID))]
    (<!! (command/handle-cmd number (account/->OpenAccount number "EUR" party party) event-store))
    number))

(defn state [id stream-ns event-store]
  (let [[state err]
        (<!! (aggregate/load-state! {} id stream-ns event-store))]
    state))

(defn account-state [number {event-store :event-store}]
  (state number `seed.accounts.account event-store))

(defn transfer-state [id {event-store :event-store}]
  (state id `seed.accounts.transfer event-store))

(defn transfer-money [from to amount {:keys [event-store]}]
  (let [id (str (java.util.UUID/randomUUID))]
    (command/handle-cmd id (transfer/->InitiateTransfer id from to amount) event-store)
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


