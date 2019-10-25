(ns relman.states
  (:require [mount.core :as m]
            [ragtime.repl :as repl]
            [ragtime.jdbc :as jdbc]
            [hikari-cp.core :as h]
            [clj-docker-client.core :as docker]
            [environ.core :as env]
            [taoensso.timbre :as log]))

(defonce db-host (get env/env :relman-db-host "localhost"))
(defonce db-port (Integer/parseInt (get env/env :relman-db-port "5432")))
(defonce db-user (get env/env :relman-db-user "relman"))
(defonce db-name (get env/env :relman-db-name "relman"))

(m/defstate data-source
  :start (let [data-source (h/make-datasource {:adapter            "postgresql"
                                               :username           db-user
                                               :database-name      db-name
                                               :server-name        db-host
                                               :port-number        db-port
                                               :connection-timeout 5000})]
           (defonce db {:datasource data-source})
           data-source)
  :stop  (do (log/info "Stopping DB")
             (h/close-datasource data-source)))

(m/defstate migration-config
  :start {:datastore  (jdbc/sql-database {:connection-uri (format "jdbc:postgresql://%s:%d/%s?user=%s"
                                                                  db-host
                                                                  db-port
                                                                  db-name
                                                                  db-user)})
          :migrations (jdbc/load-resources "migrations")})

(m/defstate database
  :start (repl/migrate migration-config))

(m/defstate docker-conn
  :start (docker/connect)
  :stop  (do (log/info "Closing docker connection")
             (docker/disconnect docker-conn)))

(comment
  (m/start)

  (m/stop)

  (get env/env :java-home "No Java?!"))
