(ns seed.accounts.app
  (require [mount.core :refer  [defstate]]
           [seed.core.event-store :as event-store]
           [seed.core.event-bus :as event-bus]
           [seed.accounts.transfer :as transfer]
           [seed.core.process :as process]
           [seed.core.process-repo :as process-repo]
           [seed.accounts.account :as account]
           [seed.accounts.api :as api]
           [ring.middleware.json :refer  [wrap-json-body wrap-json-response]]
           [immutant.web :as web]))


(def handler
  (-> #'api/routes
      wrap-json-response
      (wrap-json-body {:keywords? true})))

(defstate accounts
  :start (process/trigger
           (process/fsm-loop transfer/pattern transfer/reducers)
           seed.accounts.transfer.TransferInitiated))

(defn start []
  (mount.core/start))



