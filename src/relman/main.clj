(ns relman.main
  (:require [mount.core :as m]
            [aleph.http :as http]
            [clojure.repl :as repl]
            [taoensso.timbre :as log]
            [relman.states :refer :all]
            [relman.api.routes :as routes])
  (:gen-class))

(def PORT 7777)

;; TODO: It's here to avoid a cyclic import from routes, figure out a better way.
(m/defstate server
  :start (do (log/infof "Relman's listening on http://0.0.0.0:%d/" PORT)
             (http/start-server routes/relman-api {:port PORT}))
  :stop  (do (log/info "Stopping HTTP")
             (.close server)))

(defn shutdown!
  [_]
  (log/info "Relman's shutting down")
  (m/stop)
  (shutdown-agents)
  (System/exit 0))

(defn -main
  "Defines the entry point of Relman.
  Starts up the HTTP API on port *7777* by default."
  [& _]
  (repl/set-break-handler! shutdown!)
  (m/start))

