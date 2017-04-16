(ns seed.accounts.app
  (:require [mount.core :refer [defstate]]
            [seed.core.event-store :as event-store]
            [seed.core.event-bus :as event-bus]
            [seed.core.config :refer [config]]
            [seed.core.process :as process]
            [seed.accounts.account :as account]
            [seed.accounts.api :as api]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [immutant.web :as web]))

(def handler
  (-> #'api/routes
      wrap-json-response
      (wrap-json-body {:keywords? true})))

(defstate server
  :start (web/run handler (:web config))
  :stop (web/stop))

(defn start []
  (mount.core/start))



