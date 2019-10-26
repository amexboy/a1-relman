(ns relman.templates.core-tests
  (:require [clojure.test :refer :all]
            [relman.test-utils :as tu]
            [relman.templates.core :refer :all]
            [schema.core :as s]
            [relman.api.schemas :as schemas]))

(deftest testi
  (testing "hell"
    (is (= 1 5))))

(deftest get-templates
  (testing "Filter pipelines"
    (with-redefs-fn {#'db/get-templates           (fn [_ ]
                                                    [{
                                                      :name "test:Test"
                                                      :template "<p>Hello {{name}}</p>"
                                                      :created-by "amanu"
                                                      :required-args "name,version,service"}])}
      #(is (= [{:name "test:Test"
                :template "<p>Hello {{name}}</p>"
                :created-by "amanu"
                :required-args ["name" "version" "service"]}]
              (->> (get-templates)
                   :body
                   (s/validate schemas/TemplatesResponse)))))))
(deftest create
  (testing "Creating pipeline"
    (let [template " <p>This is a template to release some service version: {{version}}</p> <h2>Jiras</h2> {{jiras}} <h1>Expected</h1> {{expectedOutcome}} </ul> "]
      (with-redefs-fn {#'db/insert-templates       (fn [_ template]
                                                     (tu/check-and-fail
                                                      #(= {:name ("name")
                                                           :template template
                                                           :required-args ["service" "version" "expectedOutcome" "jiras"]}
                                                          template)))}
        #(is (= "Template created"
                (-> @(create {:name "name"
                              :template template})
                    :body
                    :message)))))))
