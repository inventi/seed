(ns seed.process-repo
  (use korma.db)
  (use korma.core)
  (require [com.stuartsierra.component :as component]))

(defn datasource [config]
  (let  [korma-pool (:datasource
                      (korma.db/connection-pool (korma.db/postgres config)))]
    (.setCheckoutTimeout korma-pool 2000)
    (.setTestConnectionOnCheckout korma-pool true)
    {:make-pool? false
     :datasource korma-pool}))

(defrecord ProcessRepo [config]
  component/Lifecycle

  (start [component]
    (if-not (:pool component)
      (let [pool (datasource (:config component))]
        (assoc
          component
          :pool pool
          :db (create-db pool)))
      component))

  (stop [component]
    (if-let [pool (:pool component)]
      (do
        (.close (:datasource pool))
        (assoc component
               :pool nil
               :db nil))
      component)))

(defn new-process-repo []
  (->ProcessRepo {:db  "seed"
                  :user  "seed"
                  :password  "seed"
                  :host  "192.168.99.100"}))


(declare process)

(defentity process
  (pk :id))

(def var-mapping
  {:accepted? :accepted
   :state-index :state_index
   :start-index :start_index
   :stream-index :stream_index})

(defn with-type [record]
  (assoc
    record
    :type (.getName (type record))))

(defn serialize-state [state]
  (assoc
    (dissoc
      (clojure.set/rename-keys state var-mapping)
      :checkpoint)
    :value
    (.getBytes (clojure.data.json/write-str (:value state)))))

(defn uuid [id]
  (if (string? id)
    (java.util.UUID/fromString id)
    id))

(defn new? [id]
  (empty?
    (select process
            (where {:id (uuid id)}))))

(defn timeout? [e]
  (.contains  (.getMessage e) "timed out"))

(defn with-retry [f]
  (loop [retries 0]
    (let [result (try (f)
                      (catch java.sql.SQLException e
                        (if (timeout? e)
                          :timeout (throw e))))]
      (if-not (= result :timeout)
        result
        (do
          (println "retrying " retries)
          (recur (inc retries)))))))

(defn save-state! [state id process-repo]
  (with-db
    (:db process-repo)
    (if (new? id)
      (insert process
              (values
                (assoc
                  (serialize-state state)
                  :id (uuid id))))
      (update process
              (set-fields
                (serialize-state state))
              (where {:id (uuid id)}))))
  state)

(defn save-state-with-retry! [state id process-repo]
  (with-retry #(save-state! state id process-repo)))

(defn int->long [m]
 (into {} (for [[k v] m] (if (integer? v)
                           [k (long v)]
                           [k v]))))

(defn deserialize-state [state]
  (int->long
    (clojure.set/rename-keys
    (assoc state
           :value (clojure.data.json/read-str (String. (:value state))
                                              :key-fn keyword)
           :checkpoint nil)
    (clojure.set/map-invert var-mapping))))

(defn load-state! [id process-repo]
  (with-db
    (:db process-repo)
    (if-not (new? id)
      (deserialize-state
        (first
          (select process
                  (where {:id (uuid id)})))))))

(defn load-state-with-retry! [id process-repo]
  (with-retry #(load-state! id process-repo)))

