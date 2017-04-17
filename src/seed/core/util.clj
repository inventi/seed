(ns seed.core.util
  (:require [clojure.string :as st :refer [lower-case upper-case]]
           [clojure.core.memoize :refer [memo]]))

(defn- uppercase? [c]
  (Character/isUpperCase c))

(defn- lispify [s c]
  (if (uppercase? c)
    (str s "-" c)
    (str s c)))

(defn lisp->camel [string]
  (->>
    (st/split string #"-")
    (map #(apply str
                 (upper-case (first %))
                 (rest %)))
    st/join))

(defn camel->lisp [string]
  (lower-case (reduce lispify string)))

(defn keywordize [st]
  (keyword (camel->lisp st)))

(defn keywordize-name [o]
  (when o
    (-> o
        type
        .getSimpleName
        keywordize)))

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
  (memo
    (fn [event-type]
      (let [cls-name (st/replace event-type #"-" "_")]
        (when-let [event-class ^Class (resolve (symbol cls-name))]
          (eval `(new ~(symbol cls-name)
                      ~@(ctor-args event-class))))))))

(defn error [e]
  [nil e])

(defn success [r]
  [r nil])
