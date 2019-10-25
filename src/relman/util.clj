(ns relman.util
  (:require [ring.util.http-response :as res]
            [relman.states :as states])
  (:import (java.util UUID)))

(def id-length 12)

(defn respond
  "Simple decorator for wrapping a message in the response format."
  [msg]
  (res/ok {:message msg}))

(defn service-unavailable
  "Decorator for returning code 503 Service Unavailable."
  [error]
  (res/service-unavailable {:message error}))

(defn get-id [] (str (UUID/randomUUID)))
