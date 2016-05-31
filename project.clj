(defproject seed "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [automat  "0.1.3"]
                 [org.clojure/core.async  "0.2.374"]
                 [com.geteventstore/eventstore-client_2.11 "2.1.1"]
                 [org.clojure/data.json  "0.2.6"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.stuartsierra/component  "0.3.1"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [korma  "0.4.0"]
                 [org.postgresql/postgresql  "9.4-1206-jdbc42"]
                 [compojure  "1.4.0"]
                 [org.immutant/web  "2.1.2"]
                 [ring/ring-json  "0.4.0"]
                 ]
  :java-source-paths  ["src"]
  :repl-options {:timeout 120000})
