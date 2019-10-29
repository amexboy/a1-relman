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
  (let [param-keyword (keyword parameter)
        list (get list-parameters param-keyword)
        default-value (get resolved-data param-keyword)
        prompt (namify parameter)]
    (if
        (some? list) {:type "static_select",
                      :placeholder
                      {:type "plain_text"
                       :text prompt
                       :emoji true},
                      :initial_option {:text {:type "plain_text"
                                              :text default-value
                                              :emoji true}
                                       :valuea default-value
                                       }
                      :options (map (fn [i]
                                      {:text {:type "plain_text"
                                              :text i
                                              :emoji true},
                                       :value i}) list)}
        {:type "input"
         :label
         {:type "plain_text"
          :text prompt
          :emoji true}
         :element {:type "plain_text_input"
                   :initial_value (get resolved-data param-keyword)
                   :multiline (str/starts-with? parameter "{")}
         :optional true})))


(defn command
  [trigger-id response-url user-id user-name channel-name text command]
  (f/try-all [; TODO: Add validation on text
              text (f/assert-with some? text
                                  "command text is required.\nTry /release_note template-name [service-name]")
              [template-name service-name]  (str/split text #" ")
              resolved-data (if (some? service-name)
                             (templates/fetch-data service-name)
                             {})
              required-args @(templates/required-args template-name)
              _  (log/debugf "Well, this are the required args %s" required-args)
              form-elements (mapv (partial block-element resolved-data) required-args)
              text-blocks [{:type "section",
                            :text
                            {:type "plain_text",
                             :text
                             (format ":wave: Hey <@%s>!\n\nPlease fill in the following form to request your release note" user-id)
                             :emoji true}}
                           {:type "divider"}]
              blocks (concat text-blocks form-elements)
              _  (log/debugf "Prepared blocks %s" blocks)
              request {:trigger_id trigger-id
                       :blocks blocks}
              _  (log/debugf "Prepared request %s" request)
              _ (comment s-chat/post-message states/slack-auth channel-name "Does this matter" request)
              ]
             (res/ok request)
             (f/when-failed [err]
                            (log/warn err)
                            (res/ok {:response_type "ephemeral"
                                     :text (f/message err)}))))

(with-redefs-fn {#'templates/required-args (fn [_] (future ["hello" "world"]))}
  #(-> (command "i" "i" "user-id" "" "" "text" "command")
       (json/generate-string)))

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

