(ns user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer (refresh)]
            [clojure.core.async :refer [<!!]]
            [seed.accounts.app :as app]
            [seed.accounts.account :as account]
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
  (let [acc (app/openaccount! "g1" system)]
    (def x1 (str (:number acc)))
    (<!! (:chan acc))
    (app/debitaccount! x1 800 system))
  (def x2  (str  (:number (app/openaccount! "g2" system)))))

