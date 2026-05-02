(ns legba.llm.providers.ollama
  (:require [org.httpkit.client :as http-client]
            [schema.core :as schema]
            [legba.json :refer [->json <-json]]))

(def default-base-url "http://localhost:11434")


(schema/defschema ClientOptions
  "Options for the Ollama client."
  {[schema/optional-key :base-url] schema/Str
   [schema/optional-key :token] schema/Str})


(defn- get-headers [options]
  (let [validated-options (schema/validate ClientOptions options)
        base-headers {"Content-Type" "application/json"}
        with-token (when (:token validated-options)
                     {"Authorization" (str "Bearer " (:token validated-options))})
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


(schema/defschema Model
  "A model in Ollama."
  {:model schema/Str
   :name schema/Str
   :modified_at schema/Str
   :size schema/Int
   :digest schema/Str
   (schema/optional-key :remote_model) schema/Str
   (schema/optional-key :remote_host) schema/Str
   :details {
             :format schema/Str
             :family schema/Str
             :families [schema/Str]
             :parameter_size schema/Str
             :quantization_level schema/Str
   }})

(schema/defschema ModelTagsResponse
  "Response from the /api/tags endpoint."
  {:models [Model]})

(defn list-tags
  "List all the tags for all the models in Ollama."
  ([] (list-tags {:base-url default-base-url}))
  ([options]
  (let [response (do-get "/api/tags" options)]
    (schema/validate ModelTagsResponse (<-json (:body response) :schema ModelTagsResponse)))))

(schema/defschema EmbedRequest
  "A request to generate embeddings."
  {
   :model schema/Str
   :input schema/Str
   (schema/optional-key :dimensions) schema/Int
   (schema/optional-key :keep_alive) schema/Str
   (schema/optional-key :options) {(schema/optional-key :seed) schema/Int
                                   (schema/optional-key :temperature) schema/Num
                                   (schema/optional-key :num_ctx) schema/Int}
  })

(schema/defschema EmbedResponse
  "A response from the /api/embeddings endpoint."
  {
   :model schema/Str
   :embeddings [[schema/Num]]
   :total_duration schema/Int
   :load_duration schema/Int
   :prompt_eval_cont schema/Int
  })

(defn generate-embeddings
  "Generate embeddings for a given input."
  ([request] (generate-embeddings request {:base-url default-base-url}))
  ([request options]
   (let [body (schema/validate EmbedRequest request)
         response (do-post "/api/embeddings" body options)]
     (schema/validate EmbedResponse (<-json (:body response) :schema EmbedResponse)))))

(schema/defschema ToolCall
  "A tool call in a chat request."
  {
   :name schema/Str
   (schema/optional-key :description) schema/Str
   :args {schema/Str schema/Any}
  })

(schema/defschema Tool
  "A tool in a chat request."
  {
   :type schema/Str
   :function {
              :name schema/Str
              (schema/optional-key :description) schema/Str
              :parameters {schema/Str schema/Any}
   }
  })

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