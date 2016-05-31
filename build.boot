(ns boot.user
  (:require [boot.core :refer :all]))

(set-env!
  :source-paths #{"src"}
  :target-path "target"

  :dependencies '[[me.raynes/conch "0.8.0"]
                  [org.clojure/tools.namespace  "0.2.4"]

                  [mount  "0.1.10"]

                  [org.clojure/clojure  "1.8.0"]
                  [org.clojure/core.async  "0.2.374"]

                  [automat  "0.1.3"]
                  [com.geteventstore/eventstore-client_2.11 "2.1.1"]
                  [org.clojure/data.json  "0.2.6"]
                  [org.clojure/tools.logging "0.3.1"]
                  [com.stuartsierra/component  "0.3.1"]
                  [org.clojure/tools.namespace "0.2.11"]
                  [korma  "0.4.0"]
                  [org.postgresql/postgresql  "9.4-1206-jdbc42"]
                  [compojure  "1.4.0"]
                  [org.immutant/web  "2.1.2"]
                  [ring/ring-json  "0.4.0"]])

(deftask dev []
  (set-env! :dependencies (into (get-env :dependencies)
                                '[[ring/whatever "1.2.3"]]))
  (println (get-env :dependencies)))

(task-options!
  pom {:project 'my-project
       :version "0.1.0"})

;(def system
;  "A Var containing an object representing the application under
;  development."
;  nil)

;(defn start
;  "Starts the system running, updates the Var #'system."
;  []
;  (let [sys (eval '(do (require 'app-dev) (app-dev/start-dev!)))]
;    (alter-var-root #'system (constantly sys))))




