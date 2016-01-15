(ns seed.util
  (require [clojure.string :refer [lower-case]]))

(defn- uppercase? [c]
  (Character/isUpperCase c))

(defn- lispify [s c]
  (if (uppercase? c)
    (str s "-" c)
    (str s c)))

(defn camel->lisp [string]
  (lower-case (reduce lispify string)))

(defn lispy-name [o]
  (-> o
      type
      .getSimpleName
      camel->lisp))

(defn keywordize-name [o]
  (keyword (lispy-name o)))

(defn keywordize-exception [e]
  (->>
    (.getSimpleName (type e))
    (drop-last 9) ;drop Exception
    (apply str)
    camel->lisp
    keyword))

(defn get-package [o]
 (.substring o 0 (.lastIndexOf o ".")))

(defn get-namespace [o]
  (symbol
    (-> o type .getName get-package)))

(defn ctor-args-max [rc]
  (->> (.getConstructors rc)
       (map #(count (.getParameterTypes %)))
       (apply max)))

(defn ctor-args [rc]
  (drop 2 ;default extra args
        (repeat (ctor-args-max rc) nil)))

(def new-empty-event
  (memoize
    (fn [event-type]
      (when-let [event-class ^Class (resolve (symbol event-type))]
        (eval `(new ~(symbol event-type)
                    ~@(ctor-args event-class)))))))

(defn error [e]
  [nil e])

(defn success [r]
  [r nil])
