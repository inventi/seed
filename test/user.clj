(ns user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer (refresh)]
            [clojure.core.async :refer [<!!]]
            [seed.accounts.api :as api]
            [seed.accounts.app :as app]
            [seed.accounts.account :as account]
            [seed.core.command :as command]
            [clojure.core.memoize :refer [memo-clear!]]))

(def system nil)

(defn init []
  (alter-var-root #'system
    (constantly (app/accounts-system))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system
    (fn [s] (when s (component/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (memo-clear! seed.core.util/new-empty-event)
  (stop)
  (refresh :after 'user/go))

(defn acc [system]
  (def x1 (api/openaccount! "g1" system))
  (command/handle-cmd x1 (account/->DebitAccount x1 800 "EUR") (:event-store system))
  (def x2  (api/openaccount! "g2" system)))

