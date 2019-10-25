(ns relman.api.middleware)

(defn ignore-trailing-slash
  "Middleware to ignore trailing slashes at the end of routes
  as /status and /status/ are considered different."
  [handler]
  #(let [uri ^String (:uri %)]
     (handler (assoc % :uri (if (and (not= "/" uri)
                                     (.endsWith uri "/"))
                              (subs uri 0 (dec (count uri)))
                              uri)))))
