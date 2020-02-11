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
     :block_id parameter
     :label
     {:type "plain_text"
      :text prompt
      :emoji true}
     :element (if (some? options)
                (merge {:type "static_select"
                        :action_id "value"
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
                 :action_id "value"
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

(defn cmd-list
  [trigger-id response-url user-id user-name channel-name text command])

(defn- cmd-release-note
  [trigger-id response-url user-id user-name channel-name text command]
  (f/try-all [; TODO: Add more validation on text
              [template-name service-name]  (-> (f/assert-with some? text
                                            "command text is required.\nTry /release_note template-name [service-name]")
                                 (str/split #"[,]"))
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
              view {:type "modal"
                    :callback_id (format "release-note:%s" template-name)
                    :title {:type "plain_text"
                            :text "New Release Note"
                            :emoji true}
                    :submit {:type "plain_text"
                             :text "Submit :ethiopia_parrot:"
                             :emoji true}
                    :close {:type "plain_text"
                            :text "Cancel :sad_parrot:"
                            :emoji true}
                    :blocks blocks}
              request {:trigger_id trigger-id
                       :view view}
              _  (log/debugf "Prepared request to send to channel %s %s" channel-name request)
              _  (slack-send "views.open" request)]
             (res/ok {:response_type "ephemeral"
                      :text "Opening modal"})
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

(defn modal-submit
  [response-type body]
  (f/try-all [{{user-id      :id
                username     :username}   :user
               trigger-id                 :trigger_id
               {callback-id  :callback_id
                values       :values
                :as          view}        :view
               {message-ts   :ts}         :message
               {channel-name :name}       :channel
               [{action-id   :action_id
                 block-id    :block_id}]  :actions
               } body]))


(defn slack-response [payload]
  (log/debugf "We got payload %s" payload)
  (f/try-all [{response-type :type :as body} (-> payload
                                                 (codec/url-decode)
                                                 (json/parse-string true))
              handler        (case response-type
                               "view_submission" modal-submit
                               :default (fn [&_]))]
             (apply handler response-type body))

(comment
  (slack-response (json/generate-string {:type "view_submission",:team {:id "T02CP6RJP", :domain "wkda-eng"},:user {:id "UH1T18LAY",:username "amanuel.mekonnen",:name "amanuel.mekonnen",:team_id "T02CP6RJP"},:api_app_id "APUEJEEUV",:token "jSRsKwxzW1cJnUY4fl9taZeb",:trigger_id "830908304022.2431229635.6dcdcf83edbbd1232e00c34db1626dbe",:view {:hash "1573393014.3724010b",:notify_on_close false,:callback_id "release-note:V-1.05",:app_installed_team_id "T02CP6RJP",:private_metadata "",:app_id "APUEJEEUV",:root_view_id "VPZSRCWSW",:submit {:type "plain_text", :text "Submit :ethiopia_parrot:", :emoji true},:type "modal",:state {:values {:tmG3s {:nLi5 {:type "plain_text_input", :value "1"}},:Mtpf {:SNs {:type "plain_text_input", :value "1"}},:mQZ5 {:nwL {:type "plain_text_input", :value "1"}},:Kz+tF {:Jfo {:type "plain_text_input", :value "1"}},:LC=oh {:Un0 {:type "static_select",:selected_option {:text {:type "plain_text", :text "refund", :emoji true},:value "refund"}}}}},:close {:type "plain_text", :text "Cancel :sad_parrot:", :emoji true},:title {:type "plain_text", :text "New Release Note", :emoji true},:previous_view_id nil,:external_id "",:id "VPZSRCWSW",:blocks [{:type "section",:block_id "72xc",:text {:type "plain_text",:text ":wave: Hey &lt;@amanu&gt;\n\nPlease fill in the following form to request your release note",:emoji true}} {:type "divider", :block_id "YzTZ"} {:type "input",:block_id "tmG3s",:label {:type "plain_text", :text "downTime", :emoji true},:optional false,:element {:type "plain_text_input",:initial_value "",:multiline false,:action_id "nLi5"}} {:type "input",:block_id "Mtpf",:label {:type "plain_text", :text "epic", :emoji true},:optional false,:element {:type "plain_text_input",:initial_value "",:multiline false,:action_id "SNs"}} {:type "input",:block_id "mQZ5",:label {:type "plain_text", :text "{jiras", :emoji true},:optional false,:element {:type "plain_text_input",:initial_value "",:multiline true,:action_id "nwL"}} {:type "input",:block_id "Kz+tF",:label {:type "plain_text", :text "version", :emoji true},:optional false,:element {:type "plain_text_input",:initial_value "",:multiline false,:action_id "Jfo"}} {:type "input",:block_id "LC=oh",:label {:type "plain_text", :text "service", :emoji true},:optional false,:element {:type "static_select",:placeholder {:type "plain_text", :text "service", :emoji true},:options [{:text {:type "plain_text", :text "refund", :emoji true},:value "refund"} {:text {:type "plain_text", :text "claim", :emoji true},:value "claim"}],:action_id "Un0"}}],:team_id "T02CP6RJP",:clear_on_close false,:bot_id "BPFMNBFU2"}})))

(defn approve
  [payload]
  (log/debugf "We got payload %s" payload)
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
                            (log/warn err)
                            (res/status "Failure" 500))))

