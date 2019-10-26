(ns relman.test-utils
  (:require [clojure.set :as s]))

(defn check-and-fail
  ([pred]
   (check-and-fail pred "Unexpected args!"))
  ([pred ^String message]
   (when-not (pred)
     (throw (Exception. message)))))

