

(ns relman.util-test
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest testing is]]
            [relman.util :refer :all]))

(defspec respond-returns-a-ring-response
  100
  (prop/for-all [msg gen/string]
                (= (respond msg) {:body    {:message msg}
                                  :headers {}
                                  :status  200})))

(defspec respond-returns-service-unavailable
  100
  (prop/for-all [msg gen/string]
                (= (service-unavailable msg) {:body    {:message msg}
                                              :headers {}
                                              :status  503})))

(s/def ::container-id
  (s/and string?
         #(> (count %) id-length)))

(defspec format-id-formats-given-id
  100
  (prop/for-all [msg (s/gen ::container-id)]
    (<= (count (format-id msg)) id-length)))

(def UUID-pattern #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

(deftest get-id-test
  (testing "Unique id generation"
    (is (re-matches UUID-pattern (get-id)))
    (let [id1 (get-id)
          id2 (get-id)]
      (is (not= id1 id2)))))
