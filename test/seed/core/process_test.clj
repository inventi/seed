(ns seed.core.process-test
  (:require [clojure.core.async :as async :refer [go <!! >!! chan to-chan]]
            [automat.core :as a]
            [seed.core.command :as command]
            [seed.core.util :refer [error]])
  (:use [clojure.test]
        [seed.core.process])
  (:import [seed.core.process.manager TriggerProcess StepProcess]))

(defn er-cmd [& args]
  (go {::command/error "simple error"}))

(defn ok-cmd [& args]
  (go {}))

(defn get-second-failing []
  (let [i (atom 0)]
    (fn [& args]
      (if (= @i 0)
        (do
          (swap! i inc)
          (ok-cmd args))
        (er-cmd args)))))

(defn get-verify-cmd [i]
  (fn [state cmd met]
      (cond
        (and (= @i 0) (instance? TriggerProcess cmd))
        (do (swap! i inc) (ok-cmd state cmd met))
        (and (> @i 0) (instance? StepProcess cmd))
        (do (swap! i inc) (ok-cmd state cmd met))
        :else
        (go {::command/error (str "wrong cmd type: " @i " - " cmd)}))))

(defn loop-events [& args]
  (let [ch (to-chan args)]
    (<!! ((fsm-loop [:a :b] []) ch "process-id" {:event-type "trigger"}))
    (<!! ch)))

(deftest test-process
  (testing "should returin nill when trigger command fails"
    (is (nil?
          (with-redefs
            [command/handle-cmd er-cmd]
            ((fsm-loop [:a :b] []) nil "process-id" {})))))

  (testing "should close process when second command fails"
    (is (= {:b :b}
           (with-redefs
             [command/handle-cmd (get-second-failing)]
             (loop-events {:a :a} {:b :b})))))

  (testing "should close on ProcessClosed event"
    (is (= {:b :b}
           (with-redefs
             [command/handle-cmd ok-cmd]
             (loop-events {:a :a} {:event-type "ProcessClosed"} {:b :b})))))

  (testing "should pass events as command"
    (is (= [{:end nil} 4]
           (let [i (atom 0)]
             (with-redefs
               [command/handle-cmd (get-verify-cmd i)]
               (conj
                 []
                 (loop-events
                   {:evt 1} {:evt 2} {:evt 3}
                   {:event-type "ProcessClosed"}
                   {:end nil})
                 @i)))))))

