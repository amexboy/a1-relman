(ns relman.api.routes
  (:require [compojure.route :as route]
            [compojure.api.sweet :as rest]
            [schema.core :as s]
            [relman.api.schemas :as schema]
            [relman.services.core :as services]
            [relman.slack.core :as slack]
            [relman.templates.core :as templates]
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

      (rest/GET "/services" []
        :return schema/ServicesResponse
        :summary "Returns the list of configured servies"
        (services/get-services))

      (rest/POST "/services" []
        :return schema/SimpleResponse
        :body [service schema/Service]
        (services/create service))

      (rest/GET "/templates" []
                :return schema/TemplatesResponse
                (templates/get-templates))

      (rest/POST "/templates" []
                 :return schema/SimpleResponse
                 :body [template schema/TemplateRequest]
                 (templates/create template))

      (rest/GET "/templates/:name/service/:service/args" []
                :return schema/ArgsResponse
                :path-params [name :- String
                              service :- String]
                (templates/args name service))

      (rest/POST "/templates/:name/preview" []
                :return schema/PreviewResponse
                :body [request schema/PreviewRequest]
                :path-params [name :- String]
                (templates/preview name request))

      (rest/POST "/templates/:template/service/:service/request" []
                 :return schema/SimpleResponse
                 :body [request schema/PreviewRequest]
                 :path-params [template :- String
                               service :- String]
                 (templates/request template service request))

      (rest/POST "/slack"
                 []
                 :consumes ["application/x-www-form-urlencoded"]
                 :return schema/SimpleResponse
                 :form-params [payload :- String]
                 (slack/slack-response payload))

      (rest/POST "/slack-command"
                 []
                 :consumes #{"application/x-www-form-urlencoded"}
                 :return schema/SlackPayload
                 :form-params [trigger_id     :- s/Any
                               token          :- s/Any
                               team_id        :- s/Any
                               response_url   :- s/Any
                               team_domain    :- s/Any
                               user_id        :- s/Any
                               user_name      :- s/Any
                               channel_id     :- s/Any
                               channel_name   :- s/Any
                               text           :- s/Any
                               command        :- s/Any]
                 (slack/command trigger_id response_url user_id user_name channel_name text command))

    (rest/undocumented
     (route/not-found (u/respond "Took a wrong turn?")))))))
