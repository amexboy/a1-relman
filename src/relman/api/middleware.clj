(ns relman.api.middleware
  (:require [taoensso.timbre :as log]
            [clojure.java.io :as io]))


(defn ignore-trailing-slash
  "Middleware to ignore trailing slashes at the end of routes
  as /status and /status/ are considered different."
  [handler]
  #(let [uri ^String (:uri %)]
     (comment log/debugf "Request params %s " (if (some? (:body %)) (slurp (:body %))))
     (handler (assoc % :uri (if (and (not= "/" uri)
                                     (.endsWith uri "/"))
                              (subs uri 0 (dec (count uri)))
                              uri)))))
