(ns relman.templates.core
  (:require [clojure.string :as str]
            [ring.util.http-response :as res]
            [manifold.deferred :as d]
            [clj-http.client :as client]
            [cljstache.core :refer [render]]
            [ring.util.codec :as codec]
            [failjure.core :as f]
            [cheshire.core :as json]
            [clj-slack.chat :as s-chat]
            [clj-slack.usergroups :as s-usergroups]
            [watney.core :as to-mrkdwn]
            [taoensso.timbre :as log]
            [relman.util :as u]
            [relman.templates.db :as db]
            [relman.services.db :as sdb]
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
                                  (re-seq #"\{\{(\{?[a-zA-Z.]+)\}?\}\}")
                                  (mapv last)
                                  (filter not-empty)
                                  set)
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

(defn required-args
  [template-name]
  (d/let-flow [{:keys [required-args] :as template} (db/get-template
                                                     states/db
                                                     {:name template-name})
               _ (log/debugf "Fetched required args %s" template)]
              (if required-args
                (str/split required-args #",")
                [])))

(defn args
  "Retrives the list of args"
  [name service]
  (d/let-flow [_ (log/debugf "Requesting args for %s and %s " name service)
               required-args (required-args name)
               defaults (fetch-data service)
               _ (log/debugf "Fetched defaults %s" defaults)]
              (res/ok {:args required-args
                       :defaults defaults})))

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

(defn get-group-id
  [handle]
  (->> (s-usergroups/list states/slack-auth)
       :usergroups
       (map #(select-keys % [:handle :id]))
       (filter #(= (:handle %) handle))
       first
       :id))

(defn request
  [template-name service data]
  (f/try-all [inserted (db/insert-request states/db
                                          {:template template-name
                                           :request-for service
                                           :args (json/generate-string data)
                                           :created-by "amanu"})
              _ (log/debugf "Inserted %s" inserted)
              request-id (last inserted)
              _ (log/debugf "Well this is what we returned %s" inserted)

             {:keys [slack-channel slack-group]} (sdb/get-service states/db {:name service})
             group-id (get-group-id slack-group)

             _ (log/debugf "Fetched group id %s for %s" group-id slack-group)
             {template :template}  (db/get-template states/db {:name template-name})

             _ (log/debugf "Fetched template %s" template)
             sample (to-mrkdwn/convert (format-template template data))
             message (-> (str "Dear <!subteam^%s>\n"
                              "Who ever is responsibe for sending release notes, "
                              "please checkout this request.\nThe approve button"
                              "is valid for the next 5 mintues. After which you will"
                              " have to login by following the Go To Page button :shrug:")
                         (format group-id))
             blocks {:blocks [{:type "section"
                               :text {:text message
                                      :type "mrkdwn"}}
                              {:type "divider"}
                              {:type "section"
                               :text {:text sample
                                      :type "mrkdwn"}}
                              {:type "divider"}
                              {:type "actions"
                               :block_id (format "release-note %s" request-id)
                               :elements [{:type "button"
                                           :action_id "approve"
                                           :style "primary"
                                           :text {:text "Approve"
                                                  :type "plain_text"}}
                                          {:type "button"
                                           :action_id "view"
                                           :style "danger"
                                           :url (format "https://amanu.serveo.net/request/%s" request-id)
                                           :text {:text "Go To Page"
                                                  :type "plain_text"}}]}]}
              _ (s-chat/post-message states/slack-auth slack-channel message blocks)
              _ (log/debugf "Response %s" _)]
             (res/ok {:message "OK!"})
             (f/when-failed [err]
                            (log/warn err)
                            (res/status {:message "Failed "} 500))))

(defn validate-user
  [username request-id]
  true)

(comment



  (create {:name "V-1.05" :template "
<h1>Releasing {{service}}</h1>

<p>Version: <strong>{{version}}</strong>
<p>Down Time: <strong>{{downTime}}</strong>

<h2>Release Details</h2>
<p>Epic: <a href=\"http://wkda.atlassian.net/browse/{{epic}}\">{{epic}}</a>

<h3>JIRA Tickets in this realease</h3>
<p>{{{jiras}}}</p>

<p>Thank you</p>
"}))
