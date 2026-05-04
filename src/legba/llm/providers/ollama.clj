(ns legba.llm.providers.ollama
  (:require [org.httpkit.client :as http-client]
            [schema.core :as schema]
            [legba.json :refer [->json <-json]]
            [malli.core :as malli]))

(def default-base-url "http://localhost:11434")

(def ClientOptions
  [:map
   [:base-url :maybe :string]
   [:token :maybe :string]])


(defn- get-headers [options]
  (when (not (malli/validate ClientOptions options))
    (throw (Exception. (str "Invalid options: " options)))
  (let [
        base-headers {"Content-Type" "application/json"}
        with-token (when (:token options)
                     {"Authorization" (str "Bearer " (:token options))})
        headers (merge base-headers with-token)]
    headers))

(defn- get-base-url [options]
  (if (:base-url options)
    (:base-url options)
    default-base-url))

(defn- do-post [path body options]
  (let [headers (get-headers options)
        base-url (get-base-url options)
        url (str base-url path)]
    @(http-client/post url {:body (->json body) :headers headers})))

(defn- do-get [path options]
  (let [headers (get-headers options)
        base-url (get-base-url options)
        url (str base-url path)]
    @(http-client/get url {:headers headers})))

(def Model
  [:map
   [:model :string]
   [:name :string]
   [:modified_at :string]
   [:size :int]
   [:digest :string]
   [:remote_model :maybe :string]
   [:remote_host :maybe :string]
   [:details [:map
              [:format :string]
              [:family :string]
              [:families [:vector :string]]
              [:parameter_size :string]
              [:quantization_level :string]]]]))

(def ModelTagsResponse
  "Response from the /api/tags endpoint."
  [:map [:models [:vector Model]]])

(defn list-tags
  "List all the tags for all the models in Ollama."
  ([] (list-tags {:base-url default-base-url}))
  ([options]
  (let [response (do-get "/api/tags" options)
        body (<-json (:body response))]
    (when (not (malli/validate ModelTagsResponse body))
      (throw (Exception. (str "Invalid response: " body))))
    body)))

(def EmbedRequest
  [:map
   [:model :string]
   [:input :string]
   [:dimensions :maybe :int]
   [:keep_alive :maybe :string]
   [:options :maybe [:map
                     [:seed :maybe :int]
                     [:temperature :maybe :num]
                     [:num_ctx :maybe :int]]]])

(def EmbedResponse
  [:map
   [:model :string]
   [:embeddings [:vector :float]]
   [:total_duration :int]
   [:load_duration :int]
   [:prompt_eval_count :int]])

(defn generate-embeddings
  "Generate embeddings for a given input."
  ([request] (generate-embeddings request {:base-url default-base-url}))
  ([request options]
   (when (not (malli/validate EmbedRequest request))
     (throw (Exception. (str "Invalid request: " request))))
   (let [response (do-post "/api/embeddings" request options)
         body (<-json (:body response))]
    (when (not (malli/validate EmbedResponse body))
      (throw (Exception. (str "Invalid response: " body))))
    body)))

(def ToolCall
  [:map
   [:name :string]
   [:description :maybe :string]
   [:args [:map :string :any]]])

(def Tool
  [:map
   [:type :string]
   [:function [:map
               [:name :string]
               [:description :maybe :string]
               [:parameters [:map [::malli/default [:map-of :string :any]]]]]]])

(schema/defschema ChatMessage
  "A message in a chat request."
  {
   :role schema/Str
   :content schema/Str
   (schema/optional-key :images) [schema/Str]
   (schema/optional-key :tool_calls) [ToolCall]
  })

(schema/defschema ChatRequest
  "A request to generate a chat response."
  {
   :model schema/Str
   :messages [ChatMessage]
   :tools [Tool]
   (schema/optional-key :options) {
                                   (schema/optional-key :seed) schema/Int
                                   (schema/optional-key :temperature) schema/Num
                                   (schema/optional-key :num_ctx) schema/Int
   }
   (schema/optional-key :stream) schema/Bool
   (schema/optional-key :thinking) schema/Bool
  })

(schema/defschema ChatMessageResponse
  "A message in a chat response."
  {
   :role schema/Str
   :content schema/Str
   (schema/optional-key :thinking) schema/Str
   (schema/optional-key :images) [schema/Str]
   (schema/optional-key :tool_calls) [ToolCall]
  })

(schema/defschema ChatResponse
  "A response from the /api/chat endpoint."
  {
   :model schema/Str
   :created_at schema/Str
   :messages [ChatMessageResponse]
   :total_duration schema/Int
   :load_duration schema/Int
   :prompt_eval_count schema/Int
   :prompt_eval_duration schema/Int
   :eval_count schema/Int
   :eval_duration schema/Int
   (schema/optional-key :logprobs) {
                                    :token schema/Str
                                    :logprob schema/Num
                                    :bytes [schema/Int]
                                    :top_logprobs [{
                                                    :token schema/Str
                                                    :logprob schema/Num
                                                    :bytes [schema/Int]
                                    }]
   }
   (schema/optional-key :done) schema/Bool
   (schema/optional-key :done_reason) schema/Str
  })

(defn chat
  "Generate a chat response from a given request."
  ([request] (chat request {:base-url default-base-url}))
  ([request options]
   (let [body (schema/validate ChatRequest request)
         response (do-post "/api/chat" body options)]
     (schema/validate ChatResponse (<-json (:body response) :schema ChatResponse)))))