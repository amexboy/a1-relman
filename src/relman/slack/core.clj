(ns relman.slack.core
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
            [relman.templates.db :as tdb]
            [relman.templates.core :as templates]
            [relman.services.db :as sdb]
            [relman.states :as states]))

(def list-parameters
  {:service (fn [] ["refund" "claim"])})

(defn namify
  [param]
  (-> param
      (str/replace #"$\{" "")
      ; TODO: Consider spliting camel-case and kebab case to words
      ))

(defn block-element
  [resolved-data parameter]
  (log/debugf "Creating block element for %s" parameter)
  (let [param-keyword (keyword parameter)
        options (get list-parameters param-keyword)
        default-value (get resolved-data param-keyword)
        prompt (namify parameter)]
    {:type "input"
     :label
     {:type "plain_text"
      :text prompt
      :emoji true}
     :element (if (some? options)
                (merge {:type "static_select"
                        :placeholder
                        {:type "plain_text"
                         :text prompt
                         :emoji true}
                        :options        (map (fn [i]
                                               {:text {:type "plain_text"
                                                       :text i
                                                       :emoji true},
                                                :value i}) (options))}
                       (when (some? default-value)
                         {:initial_option {:text {:type "plain_text"
                                                  :text default-value
                                                  :emoji true}
                                           :value default-value}}))
                {:type "plain_text_input"
                 :initial_value (str default-value)
                 :multiline (str/starts-with? parameter "{")})}))

(defn slack-send
  [action request]
  (let [token (:token states/slack-auth)
        url   (str (:api-url states/slack-auth) "/" action)
        headers {:Authorization (format "Bearer %s" token)
                 :Content-Type "application/json"}
        body   (json/generate-string request) ]
    (client/post url {:headers headers
                      :body body})))

(defn slack-get-request
  [action request]
  (let [token (:token states/slack-auth)
        url   (str (:api-url states/slack-auth) "/" action)
        headers {:Authorization (format "Bearer %s" token)
                 :Content-Type "application/x-www-form-urlencoded"}]
    (client/get url {:headers headers
                      :query-params request})))

(defn slack-user-info
  [user-id]
  (let [response (slack-get-request "users.info" {:user user-id})]
    (-> response
        :body
        (json/parse-string true)
        :user
        :profile)))

(defn command
  [trigger-id response-url user-id user-name channel-name text command]
  (-> command
      (case
          "/release_note" cmd-release-note
          "/list"         cmd-list
          :default        (fn [& _]
                            (let [error (format "Invalid command %s" command)]
                              (log/debug error)
                              (res/ok {:response_type "ephemeral"
                                       :text error}))))
      (apply [trigger-id response-url user-id user-name channel-name text command])))

(defn cmd-list
  [trigger-id response-url user-id user-name channel-name text command])

(defn- cmd-release-note
  [trigger-id response-url user-id user-name channel-name text command]
  (f/try-all [; TODO: Add more validation on text
              template-name  (f/assert-with some? text
                                            "command text is required.\nTry /release_note template-name [service-name]"))
             resolved-data (if (some? service-name)
                              (templates/fetch-data service-name)
                              {})
              args @(templates/required-args template-name)
              form-elements (mapv (partial block-element resolved-data) args)
              _  (log/debugf "Created form elements %s for %s" form-elements args)
              display-name (-> (slack-user-info user-id)
                               :display_name)
              text-blocks [{:type "section",
                            :text
                            {:type "plain_text",
                             :text
                             (format ":wave: Hey <@%s>\n\nPlease fill in the following form to request your release note" display-name)
                             :emoji true}}
                           {:type "divider"}]
              blocks (concat text-blocks form-elements)
              _  (log/debugf "Prepared blocks %s" blocks)
              request {:trigger_id trigger-id
                       :view {:type "modal"
                              :title {:type "plain_text"
                                      :text "New Release Note"
                                      :emoji true}
                              :submit {:type "plain_text"
                                       :text "Submit :ethiopia_parrot:"
                                       :emoji true}
                              :close {:type "plain_text"
                                      :text "Cancel :sad_parrot:"
                                      :emoji true}
                              :blocks blocks}}
              _  (log/debugf "Prepared request to send to channel %s %s" channel-name request)
              _  (slack-send "views.open" request)]
             (res/ok {:response_type "ephemeral"
                      :text "Pleae fill in the form displayed"})
             (f/when-failed [err]
                            (log/warn err)
                            (res/ok {:response_type "ephemeral"
                                     :text (f/message err)}))))

(comment
  (with-redefs-fn {#'templates/required-args (fn [_] (future ["{hello" "epic" "epic" "jira" "{world"]))}
    #(-> (command "i" "i" "user-id" "1" "1" "V-1.03" "/release_note")
         (json/generate-string)))
  (slack-user-info "UH1T18LAY")
  (-> (command "12344" "https://hooks.slack.com/commands/T02CP6RJP/821561953958/8N7nWmOtC3wnmLwHTuZi9hAp" "12345" "amanu" "rel-man" "V-1.05" "/release_note")
      (json/generate-string)
      (log/debug)))


(defn slack-response
  [payload]
  ;; TODO: You need to check if the guy is allowed to approve
  (f/try-all [{{user-id      :id
                username     :username}   :user
               {message-ts   :ts}         :message
               {channel-name :name}       :channel
               [{action-id   :action_id
                 block-id    :block_id}]  :actions :as body} (-> payload
                                                                 (codec/url-decode)
                                                                 (json/parse-string true))
              [action-type request-id] (str/split block-id #" ")
              _                      (log/debugf "Approving reqeust %s by %s" request-id username)
              response               (format "Request approved by <@%s>! Email will be sent" user-id)
              data                   {:thread_ts message-ts}
              _                     (if (templates/validate-user username request-id)
                                      (s-chat/post-message states/slack-auth
                                                           channel-name response data))]
             (res/ok {:message "Hola"})
             (f/when-failed [err]
                            (log/warnf "Error: %s" err)
                            (res/status "Failure" 500))))

