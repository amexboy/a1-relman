(ns relman.templates.core
  (:require [clojure.string :as str]
            [ring.util.http-response :as res]
            [manifold.deferred :as d]
            [cljstache.core :refer [render]]
            [failjure.core :as f]
            [taoensso.timbre :as log]
            [relman.util :as u]
            [relman.templates.db :as db]
            [relman.states :as states]))

(defn get-templates
  "Fetches list of templates for the database"
  []
  (f/try-all [result (db/get-templates states/db)
              _ (log/debugf "Fetched templates %s" result)]
             (->> result
                  (map #(assoc % :required-args (str/split (:required-args %) #",")))
                  (res/ok))
             (f/when-failed [err]
                            (log/warnf "Failure to proces request %s" err)
                            (res/status {:message "Failed to process reqeust"} 500))))

(defn create
  "Create a new template"
  [{:keys [name template]}]
  (d/let-flow [required-args (->> template
                                  (re-seq #"\{\{(\{?[a-zA-Z]+)\}?\}\}")
                                  (mapv last)
                                  (filter not-empty))
               entry {:name name
                      :template template
                      :required-args (str/join "," required-args)
                      :created-by "amanu"}
               response (f/attempt-all [_ (db/insert-template states/db entry)]
                                       (res/ok {:message "Template created"})
                                       (f/when-failed [err]
                                                      (log/warnf "Failed to insert template %s" err)
                                                      (res/status "Failed to insert template" 500)))
               _ (log/debugf "Inserted entry %s" entry)]
              response))
(defn fetch-data
  [service]
  {:service service})

(defn args
  "Retrives the list of args"
  [name service]
  (d/let-flow [_ (log/debugf "Requesting args for %s and %s " name service)
               {:keys [required-args] :as template} (db/get-template states/db {:name name})
               _ (log/debugf "Fetched required args %s" template)
               defaults (fetch-data service)
               _ (log/debugf "Fetched defaults %s" defaults)]
              (res/ok {
                       :args (if required-args
                               (str/split required-args #",")
                               [])
                       :defaults defaults
                       })))

(defn format-template
  [template data]
  (->> (into {}
             (map
              (fn [k]
                [
                 (->> k
                      name
                      ( (fn [s] (if (str/starts-with? s "{") (subs s 1) s)) )
                      str
                      keyword)
                 (get data k)])
              (keys data))
             )
       (render template) ))

(defn preview
  [name data]
  (d/let-flow [{:keys [template]} (db/get-template states/db {:name name})
               _ (log/debugf "Validating ;-) (with the sound) %s" data)
               result (format-template template data)]
              (res/ok {:message result})))
