(ns seed.core.process-repo
  (:refer-clojure :exclude  [update])
  (require [mount.core :refer  [defstate]]
           [seed.core.config :refer [config]]
           [clojure.set :refer [rename-keys map-invert]]
           [clojure.data.json :as json])
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

(def db-keys
  {:accepted? :accepted
   :state-index :state_index
   :start-index :start_index
   :stream-index :stream_index})

(defn state->db [state]
  (->
    state
    (rename-keys db-keys)
    (dissoc :checkpoint)
    (assoc :value (.getBytes (json/write-str (:value state))))))

(defn int->long [m]
 (into {} (for [[k v] m] (if (integer? v)
                           [k (long v)]
                           [k v]))))

(defn db->state [state]
  (->
    state
    (rename-keys (map-invert db-keys))
    int->long
    (assoc :checkpoint nil)
    (assoc :value (json/read-str (String. (:value state)) :key-fn keyword))))

(defn uuid [id]
  (if (string? id)
    (java.util.UUID/fromString id)
    id))

;Korma stuff
(declare process)
(defentity process (pk :id))

(defn new? [id]
  (empty?
    (select process (where {:id (uuid id)}))))

(defn save-state! [state id]
  (with-db db
    (if (new? id)
      (insert process (values (assoc (state->db state) :id (uuid id))))
      (update process (set-fields (state->db state)) (where {:id (uuid id)}))))
  state)


(defn load-state! [id]
  (with-db db
    (when-not (new? id)
      (->
        (select process (where {:id (uuid id)}))
        first
        db->state))))

