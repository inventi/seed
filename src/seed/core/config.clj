(ns seed.core.config)

(def config
  {:event-store {:host "localhost"
                 :port 1113
                 :user "admin"
                 :password "changeit"}
   :web {:port 8081 :host "0.0.0.0"}})
