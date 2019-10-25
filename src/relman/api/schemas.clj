(ns relman.api.schemas
  (:require [schema.core :as s])
  (:import (clojure.lang Keyword)))

(s/defschema SimpleResponse {:message String})

(s/defschema Service {:name             String
                      :slack-channel     String
                      :slack-group       String
                      :team             String})

(s/defschema ServicesResponse [Service])

(comment
  (s/validate ServicesResponse [{:name "refund-service"
                                 :team "mj-os"
                                 :slackChannel "a1-sales-java"
                                 :slackGroup "mosjava"}]))
