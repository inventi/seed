(ns seed.core.test.process
  (require [clojure.core.async :refer [go <!!]]
           [automat.core :as a])
  (use [seed.core.process]
       [clojure.test]))

(def simple-process
  [:a :b])

(def machine
  (a/compile simple-process))

(defn drop-unused-keys [state]
  (dissoc state :checkpoint :start-index :stream-index))

(defn state [st]
 (assoc st
       :checkpoint nil :start-index 0 :stream-index 0))

(deftest test-transition
  (is (= {:accepted? false, :state-index 1, :value  {:event  {:event-type  "A"}, :trigger-event  {:event-type  "A"}}}
         (drop-unused-keys (#'seed.process/next-state {} machine {:event-type "A"}))))

  (is (= {:accepted? true, :state-index 2, :value {:event  {:event-type  "B"}}}
         (drop-unused-keys (#'seed.process/next-state (state {:accepted? false :state-index 1}) machine {:event-type "B"})))))
