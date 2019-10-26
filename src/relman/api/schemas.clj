(ns relman.api.schemas
  (:require [schema.core :as s])
  (:import (clojure.lang Keyword)))

(s/defschema SimpleResponse {:message String})

(s/defschema Service {:name             String
                      :slack-channel     String
                      :slack-group       String
                      :team             String})

(s/defschema ServicesResponse [Service])

(s/defschema Template {
                       :name          String
                       :template      String
                       :required-args [String]
                       :created-by    String})

(s/defschema ArgsResponse {
                           :args [String]
                           :defaults {Keyword String}
                           })

(s/defschema PreviewResponse {:message String})

(s/defschema PreviewRequest {Keyword String})

(s/defschema TemplateRequest {:name        String
                              :template    String})

(s/defschema TemplatesResponse (s/either SimpleResponse
                                         [Template]))


(comment
  (s/validate ServicesResponse [{:name "refund-service"
                                 :team "mj-os"
                                 :slackChannel "a1-sales-java"
                                 :slackGroup "mosjava"}]))
