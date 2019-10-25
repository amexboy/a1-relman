(ns relman.services.core
  (:require [ring.util.http-response :as res]
            [manifold.deferred :as d]
            [failjure.core :as f]
            [taoensso.timbre :as log]
            [relman.util :as u]
            [relman.services.db :as db]
            [relman.states :as states]))

(defn get-services
  "Feteches list of servies from the database "
  []
  (f/try-all [result (db/get-services states/db)
              _ (log/debugf "Fetched items %s" result)]
             (res/ok result)
             (f/when-failed [err]
                            (log/warnf "Failed to fetch services %s" err)
                            (res/status "Request failed" 500))))

(defn create
  "Creates a service entry"
  [service]
  (f/try-all [_ (db/insert-service states/db service)
              _ (log/infof "Inserted new service %s" service)]
             (u/respond "Service Created")
             (f/when-failed [err]
                            (log/warnf "Failed to create service %s" err)
                            (res/bad-request {:message "Failed to create service"}))))

(comment db/insert-service states/db {:name "refund-service"
          :team "mj-os"
          :slackChannel "a1-sales-java"
          :slackGroup "mosjava"})

