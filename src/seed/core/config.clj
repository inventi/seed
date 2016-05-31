(ns seed.core.config)

(def config
  {:event-store {:host "192.168.99.100"
                 :port 1113
                 :user "admin"
                 :password "changeit"}
   :db {:db  "seed"
        :user  "seed"
        :password  "seed"
        :host  "192.168.99.100"}})
