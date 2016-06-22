(ns seed.core.config)

(def config
  {:event-store {:host "localhost"
                 :port 1113
                 :user "admin"
                 :password "changeit"}
   :db {:db  "seed"
        :user  "seed"
        :password  "seed"
        :host  "localhost"}})
