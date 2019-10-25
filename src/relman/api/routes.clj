(ns relman.api.routes
  (:require [compojure.route :as route]
            [compojure.api.sweet :as rest]
            [schema.core :as s]
            [relman.api.schemas :as schema]
            [relman.services.core :as services]
            [relman.util :as u]
            [relman.api.middleware :as m]))

(def relman-api
  (m/ignore-trailing-slash
   (rest/api
    {:swagger
     {:ui   "/"
      :spec "/swagger.json"
      :data {:info     {:title       "A1 Real Man"
                        :version     "0.1"
                        :description "Request for your release not here"}
             :consumes ["application/json"]
             :produces ["application/json"]}}}

    (rest/context "/api" []
      :tags ["Real Man"]

      (rest/GET "/service" []
        :return schema/ServicesResponse
        :summary "Returns the list of configured services"
        (services/get-services))

      (rest/POST "/service" []
        :return schema/SimpleResponse
        :body [service schema/Service]
        (services/create service))


    (rest/undocumented
     (route/not-found (u/respond "Took a wrong turn?")))))))
