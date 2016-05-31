(ns user
  (:require
            [clojure.tools.namespace.repl :refer (refresh)]
            [clojure.core.async :refer [<!!]]
            [seed.accounts.api :as api]
            [seed.accounts.app :as app]
            [seed.accounts.account :as account]
            [seed.core.command :as command]
            [clojure.core.memoize :refer [memo-clear!]]))


(defn acc []
  (def x1 (api/openaccount! "g1"))
  (command/handle-cmd x1 (account/->DebitAccount x1 800 "EUR"))
  (def x2  (api/openaccount! "g2")))

