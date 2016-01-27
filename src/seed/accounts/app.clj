(ns seed.accounts.app
 (require [com.stuartsierra.component :as component]
          [seed.core.event-store :as event-store]
          [seed.core.event-bus :as event-bus]
          [seed.accounts.transfer :as transfer]
          [seed.core.process :as process]
          [seed.core.process-repo :as process-repo]
          [seed.accounts.account :as account]
          [seed.accounts.api :as api]
          [ring.middleware.json :refer  [wrap-json-body wrap-json-response]]
          [immutant.web :as web]))

(defn wrap-system [handler system]
  (fn [req]
    (handler (assoc req :accounts system))))

(def handler
  (-> #'api/routes
      wrap-json-response
      (wrap-json-body {:keywords? true})))

(defrecord Accounts []
  component/Lifecycle

  (start [{:keys [event-bus event-store process-repo] :as component}]
    (let [transfer-loop (process/fsm-loop transfer/transfer-process event-store process-repo)]
      (process/trigger transfer-loop
                       seed.accounts.transfer.TransferInitiated
                       event-bus)
      (web/run (wrap-system handler component))
      component))

  (stop [component]
    component))

(defn accounts-system []
  (component/system-map
    :event-store (event-store/new-event-store)
    :event-bus (component/using
                 (event-bus/new-event-bus)
                 [:event-store])
    :process-repo (process-repo/new-process-repo)
    :accounts (component/using
                (->Accounts)
                [:event-store :event-bus :process-repo])))

