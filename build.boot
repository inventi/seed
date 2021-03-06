(ns boot.user
  (:require [boot.core :refer :all]))

(set-env!
  :source-paths #{"src" "test"}
  :target-path "target"

  :dependencies '[[me.raynes/conch "0.8.0"]
                  [org.clojure/tools.namespace  "0.2.4"]

                  [mount  "0.1.10"]

                  [org.clojure/clojure  "1.9.0-alpha14"]
                  [org.clojure/core.async  "0.2.374"]

                  [automat  "0.2.0"]
                  [com.geteventstore/eventstore-client_2.11 "2.4.0"]
                  [org.clojure/data.json  "0.2.6"]
                  [org.clojure/tools.logging "0.3.1"]
                  [org.clojure/tools.namespace "0.2.11"]
                  [org.postgresql/postgresql  "9.4-1206-jdbc42"]
                  [compojure  "1.4.0"]
                  [org.immutant/web  "2.1.2"]
                  [ring/ring-json  "0.4.0"]])

(deftask dev []
  (comp
    (javac)
    (repl)))

(defn start []
  (eval `(do
           (require 'seed.accounts.app)
           (seed.accounts.app/start))))

(defn reset []
  (eval `(do
           (clojure.core.memoize/memo-clear! seed.core.util/new-empty-event)
           (mount.core/stop)
           (mount.core/start))))

(deftask run []
  (comp
    (javac)
    (with-pre-wrap fileset
      (start)
      fileset)
    (wait)))

