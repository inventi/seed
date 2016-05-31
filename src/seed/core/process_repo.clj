(ns seed.core.process-repo
  (:refer-clojure :exclude  [update])
  (require [mount.core :refer  [defstate]]
           [seed.core.config :refer [config]])
  (use korma.db)
  (use korma.core))

(defn datasource [config]
  (let  [korma-pool (:datasource
                      (korma.db/connection-pool (korma.db/postgres config)))]
    (.setCheckoutTimeout korma-pool 2000)
    (.setTestConnectionOnCheckout korma-pool true)
    {:make-pool? false
     :datasource korma-pool}))

(defstate pool
  :start (datasource (:db config))
  :stop (.close (:datasource pool)))

(defstate db :start (create-db pool))

(declare process)

(defentity process (pk :id))

(def var-mapping
  {:accepted? :accepted
   :state-index :state_index
   :start-index :start_index
   :stream-index :stream_index})

(defn as-bytes [state]
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

(defn save-state! [state id]
  (with-db db
       (if (new? id)
         (insert process
                 (values
                   (assoc
                     (as-bytes state)
                     :id (uuid id))))
         (update process
                 (set-fields
                   (as-bytes state))
                 (where {:id (uuid id)}))))
  state)

(defn int->long [m]
 (into {} (for [[k v] m] (if (integer? v)
                           [k (long v)]
                           [k v]))))

(defn as-json [state]
  (int->long
    (clojure.set/rename-keys
    (assoc state
           :value (clojure.data.json/read-str (String. (:value state))
                                              :key-fn keyword)
           :checkpoint nil)
    (clojure.set/map-invert var-mapping))))

(defn load-state! [id]
  (with-db db
       (if-not (new? id)
         (as-json
           (first
             (select process
                     (where {:id (uuid id)})))))))

