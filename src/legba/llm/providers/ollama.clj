(ns legba.llm.providers.ollama
  "Ollama provider for interacting with the Ollama API.

   This namespace provides functions to communicate with a local or remote
   Ollama instance, supporting model listing, embeddings generation, and
   chat completions. All functions accept an optional options map that can
   specify the base URL and an authentication token."
  (:require [org.httpkit.client :as http-client]
            [schema.core :as schema]
            [legba.json :refer [->json <-json]]
            [malli.core :as malli]))

;;; ============================================================================
;;; Constants
;;; ============================================================================

(def default-base-url "http://localhost:11434")

;;; ============================================================================
;;; Schemas
;;; ============================================================================

(def ClientOptions
  "Schema for client options passed to Ollama API functions.

   WHAT: Defines the structure of the options map used by all API functions.

   WHY: Provides validation for the options parameters accepted by functions
     like get-headers, get-base-url, do-post, do-get, list-tags,
     generate-embeddings, and chat. The base-url specifies the Ollama server
     address, and the token provides optional Bearer authentication.

   ARGUMENTS: Not used directly — this schema validates the options map passed
     TO functions rather than being called itself.

   RETURNS: The malli library uses this schema for validation at runtime,
     throwing an exception if the provided map does not conform.

   EXAMPLE:
     (malli/validate ClientOptions {:base-url \"http://my-server:11434\", :token \"abc123\"})
     ;; => true

     (malli/validate ClientOptions {:invalid-key \"value\"})
     ;; => false (will throw an Exception in callers)"
  [:map
   [:base-url :maybe :string]
   [:token :maybe :string]])

(def Model
  "Schema for a model returned by Ollama's /api/tags endpoint.

   WHAT: Defines the structure of a model entry in the list of installed
     models. Includes metadata such as name, modification date, size,
     digest, remote model/host info, and detailed model information.

   WHY: Used by malli/validate to ensure responses from Ollama conform
     to the expected structure. This catches API response errors early.

   ARGUMENTS: Not called directly — used as a component of ModelTagsResponse.

   RETURNS: The malli library uses this schema for runtime validation.

   EXAMPLE:
     (malli/validate Model
       {:model \"llama3.1:latest\"
        :name \"llama3.1:latest\"
        :modified_at \"2024-01-15T10:30:00Z\"
        :size 4547000000
        :digest \"abc123def456\"
        :remote_model nil
        :remote_host nil
        :details {:format \"gguf\"
                   :family \"llama\"
                   :families [\"llama\"]
                   :parameter_size \"8B\"
                   :quantization_level \"Q4_0\"}})
     ;; => true"
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
               [:quantization_level :string]]]])

(def ModelTagsResponse
  "Schema for the response from the Ollama /api/tags endpoint.

   WHAT: Defines the structure of the response containing a vector of
     installed models.

   WHY: Used to validate the response body returned from list-tags,
     ensuring each model conforms to the Model schema.

   ARGUMENTS: Not called directly — used by malli/validate internally.

   RETURNS: The malli library uses this schema for runtime validation.

   EXAMPLE:
     (malli/validate ModelTagsResponse
       {:models [\"llama3.1:latest\"]})
     ;; Note: models here should be vectors of Model schemas, not strings.
     (malli/validate ModelTagsResponse
       {:models [{:model \"llama3.1:latest\"
                   :name \"llama3.1:latest\"
                   :modified_at \"2024-01-15T10:30:00Z\"
                   :size 4547000000
                   :digest \"abc123\"
                   :remote_model nil
                   :remote_host nil
                   :details {:format \"gguf\"
                              :family \"llama\"
                              :families [\"llama\"]
                              :parameter_size \"8B\"
                              :quantization_level \"Q4_0\"}}]})
     ;; => true"
  [:map [:models [:vector Model]]])

(def EmbedRequest
  "Schema for generating embeddings via the Ollama API.

   WHAT: Defines the request structure for the /api/embeddings endpoint.

   WHY: Validates that the caller provides all required fields (model and
     input) and optionally provides dimensions, keep_alive duration,
     model-specific options (seed, temperature, context window size).

   ARGUMENTS: Not called directly — used by generate-embeddings for validation.

   RETURNS: The malli library uses this schema for runtime validation.

   EXAMPLE:
     (malli/validate EmbedRequest
       {:model \"nomic-embed-text\"
        :input \"Hello, world!\"})
     ;; => true

     (malli/validate EmbedRequest
       {:model \"nomic-embed-text\"
        :input \"Hello, world!\"
        :dimensions 768
        :options {:temperature 0.2}})
     ;; => true"
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
  "Schema for the response from the Ollama /api/embeddings endpoint.

   WHAT: Defines the structure of an embeddings response, including the
     generated embedding vectors and timing metadata.

   WHY: Validates the response from generate-embeddings to ensure the
     returned data matches the expected shape before processing further.

   ARGUMENTS: Not called directly — used by generate-embeddings for validation.

   RETURNS: The malli library uses this schema for runtime validation.

   EXAMPLE:
     (malli/validate EmbedResponse
       {:model \"nomic-embed-text\"
        :embeddings [[0.1 0.2 -0.3 0.4]]
        :total_duration 12345678
        :load_duration 1000000
        :prompt_eval_count 4})
     ;; => true"
  [:map
   [:model :string]
   [:embeddings [:vector :float]]
   [:total_duration :int]
   [:load_duration :int]
   [:prompt_eval_count :int]])

;;; ============================================================================
;;; Private Helper Functions
;;; ============================================================================

(defn- get-headers
  "Build a map of HTTP headers for Ollama API requests.

   WHAT: Constructs the appropriate headers for making HTTP requests to the
     Ollama API. Always includes a `Content-Type` header. Optionally includes
     an `Authorization` Bearer header if a token is provided in options.

   WHY: Centralizes header construction so all API functions (post and get)
     use consistent formatting and authentication. Validating options upfront
     provides clear error messages rather than cryptic failures deeper in
     the HTTP call stack.

   ARGUMENTS:
     - options: A map conforming to `ClientOptions` schema, containing:
       - `:base-url` (optional string): The base URL of the Ollama server.
       - `:token` (optional string): An authentication token for the server.

   RETURNS: A map of header names to header values (strings to strings),
     e.g. `{\"Content-Type\" \"application/json\", \"Authorization\" \"Bearer token123\"}`.
     Always includes `Content-Type: application/json`.

   EXAMPLE:
     (get-headers {})
     ;; => {\"Content-Type\" \"application/json\"}

     (get-headers {:token \"my-secret-token\"})
     ;; => {\"Content-Type\" \"application/json\" \"Authorization\" \"Bearer my-secret-token\"}"
  (when (not (malli/validate ClientOptions options))
    (throw (Exception. (str "Invalid options: " options)))
    (let [
         base-headers {"Content-Type" "application/json"}
         with-token (when (:token options)
                         {"Authorization" (str "Bearer " (:token options))})
         headers (merge base-headers with-token)]
      headers)))

(defn- get-base-url
  "Extract the base URL for Ollama API requests from the options map.

   WHAT: Retrieves the `:base-url` value from the options map, falling back
     to `default-base-url` if no custom URL is provided.

   WHY: Provides a single point of control for determining the Ollama server
     endpoint. Callers can override the default `localhost:11434` by passing
     a custom base-url in their options, enabling use with remote Ollama
     instances or nonstandard ports.

   ARGUMENTS:
     - options: A map conforming to `ClientOptions` schema, containing:
       - `:base-url` (optional string): Custom base URL to use instead of default.

   RETURNS: A string representing the base URL for API requests, either
     the provided `:base-url` or `\"http://localhost:11434\"`.

   EXAMPLE:
     (get-base-url {})
     ;; => \"http://localhost:11434\"

     (get-base-url {:base-url \"http://remote-server.example.com:11434\"})
     ;; => \"http://remote-server.example.com:11434\""
  (if (:base-url options)
    (:base-url options)
    default-base-url))

(defn- do-post
  "Perform an HTTP POST request to the Ollama API.

   WHAT: Sends a JSON-encoded POST request to the given `path` on the
     Ollama server, with the provided `body` serialized to JSON. Uses
     built headers from `get-headers` and the base URL from `get-base-url`.

   WHY: Centralizes the mechanics of POST requests so all API functions
     (list-tags, generate-embeddings, chat) share a single implementation
     for making POST calls. Handles URL construction, header building,
     JSON serialization, and the synchronous wait on the http-kit future.

   ARGUMENTS:
     - path: A string representing the API endpoint path, e.g. \"/api/embeddings\".
     - body: A map or data structure to serialize as the JSON request body.
     - options: A map conforming to `ClientOptions` schema with :base-url
       and/or :token keys.

   RETURNS: The response map from http-kit, which typically includes keys
     like `:status` (HTTP status code), `:body` (response body string),
     and `:headers` (response headers).

   EXAMPLE:
     (do-post \"/api/embeddings\"
              {:model \"nomic-embed-text\" :input \"Hello\"}
              {:base-url \"http://localhost:11434\"})
     ;; => {:status 200, :body \"{\\\"model\\\":\\\"nomic-embed-text\\\",...}\", ...}"
  (let [headers (get-headers options)
        base-url (get-base-url options)
        url (str base-url path)]
    @(http-client/post url {:body (->json body) :headers headers})))

(defn- do-get
  "Perform an HTTP GET request to the Ollama API.

   WHAT: Sends an HTTP GET request to the given `path` on the Ollama server,
     with built headers from `get-headers`. Uses the base URL from
     `get-base-url`.

   WHY: Centralizes the mechanics of GET requests so functions like
     list-tags share a single implementation for making GET calls.
     Handles URL construction, header building, and the synchronous
     wait on the http-kit future.

   ARGUMENTS:
     - path: A string representing the API endpoint path, e.g. \"/api/tags\".
     - options: A map conforming to `ClientOptions` schema with :base-url
       and/or :token keys.

   RETURNS: The response map from http-kit, which typically includes keys
     like `:status` (HTTP status code), `:body` (response body string),
     and `:headers` (response headers).

   EXAMPLE:
     (do-get \"/api/tags\" {:base-url \"http://localhost:11434\"})
     ;; => {:status 200, :body \"{\\\"models\\\": [...]}\", ...}"
  (let [headers (get-headers options)
        base-url (get-base-url options)
        url (str base-url path)]
    @(http-client/get url {:headers headers})))

;;; ============================================================================
;;; Public API Functions
;;; ============================================================================

(defn list-tags
  "List all installed models in the Ollama instance.

   WHAT: Fetches the list of models registered with the Ollama server by
     calling the `/api/tags` endpoint, and returns the validated response.

   WHY: Provides a simple way to discover what models are available on a
     given Ollama instance. Useful for inspecting available models before
     generating embeddings or performing chat completions. Also useful for
     diagnostics — confirming the server is running and models are loaded.

   ARGUMENTS:
     - (no-args version): Uses the default base URL (`http://localhost:11434`).
     - options: A map conforming to `ClientOptions` schema, containing:
       - `:base-url` (optional string): Custom base URL for the Ollama server.
       - `:token` (optional string): Authentication token for the server.

   RETURNS: A map with a `:models` key whose value is a vector of model
     maps conforming to the `Model` schema. Each model map includes
     model name, modification date, size, digest, remote info, and details.

   EXAMPLE:
     ;; Using defaults (localhost:11434)
     (list-tags)
     ;; => {:models [{:model \"llama3.1:latest\"
     ;;                 :name \"llama3.1:latest\"
     ;;                 :modified_at \"2024-01-15T10:30:00Z\"
     ;;                 :size 4547000000
     ;;                 :digest \"abc123...\"
     ;;                 :remote_model nil
     ;;                 :remote_host nil
     ;;                 :details {:format \"gguf\"
     ;;                           :family \"llama\"
     ;;                           :families [\"llama\"]
     ;;                           :parameter_size \"8B\"
     ;;                           :quantization_level \"Q4_0\"}}]}

     ;; Using a custom server
     (list-tags {:base-url \"http://my-server.example.com:11434\"})
     ;; => {:models [...]}

     ;; Extract just the names of available models
     (map :name (list-tags))
     ;; => (\"llama3.1:latest\" \"nomic-embed-text:latest\")"
  ([] (list-tags {:base-url default-base-url}))
  ([options]
   (let [response (do-get "/api/tags" options)
         body (<-json (:body response))]
     (when (not (malli/validate ModelTagsResponse body))
       (throw (Exception. (str "Invalid response: " body))))
     body)))

(defn generate-embeddings
  "Generate vector embeddings for a given text input.

   WHAT: Sends a text input to a specified Ollama embedding model and
     returns the resulting n-dimensional vector embedding.

   WHY: Embeddings are fundamental to semantic search, similarity
     comparison, and vector-based storage in knowledge graph systems.
     This function allows clients to convert text into numerical vectors
     that can be compared, indexed, or stored.

   ARGUMENTS:
     - request: A map conforming to `EmbedRequest` schema, containing:
       - `:model` (string): The name of the embedding model to use,
         e.g. \"nomic-embed-text\" or \"all-minilm\".
       - `:input` (string): The text to generate embeddings for.
       - `:dimensions` (optional int): Truncate embeddings to this many
         dimensions.
       - `:keep_alive` (optional string): How long to keep the model
         loaded in memory, e.g. \"5m\", \"1h\".
       - `:options` (optional map): Model-specific options with keys like
         `:seed` (int), `:temperature` (num), `:num_ctx` (int).
     - options: A map conforming to `ClientOptions` schema, containing:
       - `:base-url` (optional string): Custom base URL for the Ollama server.
       - `:token` (optional string): Authentication token for the server.

   RETURNS: A map with the following keys:
       - `:model`: The model used for embedding generation.
       - `:embeddings`: A vector of float vectors (one per input text item).
       - `:total_duration`: Total time in nanoseconds.
       - `:load_duration`: Time in nanoseconds to load the model.
       - `:prompt_eval_count`: Number of tokens evaluated.

   EXAMPLE:
     ;; Basic embedding request
     (generate-embeddings
       {:model \"nomic-embed-text\"
        :input \"The quick brown fox jumps over the lazy dog.\"})
     ;; => {:model \"nomic-embed-text\"
     ;;     :embeddings [[0.023 -0.015 0.042 ...]]
     ;;     :total_duration 12345678
     ;;     :load_duration 1000000
     ;;     :prompt_eval_count 18}

     ;; With explicit dimensions and server options
     (generate-embeddings
       {:model \"nomic-embed-text\"
        :input \"This is a test of the emergency broadcasting system.\"
        :dimensions 768
        :keep_alive \"10m\"}
       {:base-url \"http://localhost:11434\"})
     ;; => {:model \"nomic-embed-text\"
     ;;     :embeddings [[0.034 -0.021 0.055 ...]]
     ;;     :total_duration 15000000
     ;;     :load_duration 2000000
     ;;     :prompt_eval_count 22}"
  ([request] (generate-embeddings request {:base-url default-base-url}))
  ([request options]
   (when (not (malli/validate EmbedRequest request))
     (throw (Exception. (str "Invalid request: " request))))
   (let [response (do-post "/api/embeddings" request options)
         body (<-json (:body response))]
     (when (not (malli/validate EmbedResponse body))
       (throw (Exception. (str "Invalid response: " body))))
     body)))

;;; ============================================================================
;;; Chat Schemas
;;; ============================================================================

(def ToolCall
  "Schema for a tool call in a chat message.

   WHAT: Defines the structure of a tool invocation returned by an LLM
     or provided by a client in a message.

   WHY: Enables the LLM to execute functions (tools) as part of a
     conversation. Used both for describing available tools and for
     parsing tool call responses from the model.

   EXAMPLE:
     (malli/validate ToolCall
       {:name \"get_weather\"
        :description \"Get the current weather for a location\"
        :args {:location \"New York\" :units \"fahrenheit\"}})
     ;; => true"
  [:map
   [:name :string]
   [:description :maybe :string]
   [:args [:map :string :any]]])

(def Tool
  "Schema for a tool definition in a chat request.

   WHAT: Defines a tool that a chat model can call. Each tool has a name,
     description, and parameters schema.

   WHY: Allows clients to provide function signatures to the LLM so it
     can invoke them during conversation. This enables 'agent-like' behavior
     where the model can call out to external functions.

   EXAMPLE:
     (malli/validate Tool
       {:type \"function\"
        :function {:name \"get_weather\"
                    :description \"Get weather for a location\"
                    :parameters {:type \"object\"
                                 :properties {:location {:type \"string\"}
                                              :units {:type \"string\"}}}})
     ;; => true"
  [:map
   [:type :string]
   [:function [:map
                [:name :string]
                [:description :maybe :string]
                [:parameters [:map [::malli/default [:map-of :string :any]]]]]]])

(schema/defschema ChatMessage
  "A message in a chat request.

   WHAT: Defines the structure of an individual message within a chat
     conversation sent to the Ollama API.

   WHY: Used to validate chat request messages before sending them to
     the API. Supports system, user, and assistant roles, with optional
     images and tool calls for vision and tool-use capabilities.

   ARGUMENTS: Not called directly — used by schema/validate internally
     within the chat function.

   RETURNS: Returns true if the provided map conforms to the schema,
     otherwise false. Throws an exception with details on failure.

   EXAMPLE:
     (schema/validate ChatMessage
       {:role \"user\"
        :content \"What is the capital of France?\"})
     ;; => true

     (schema/validate ChatMessage
       {:role \"user\"
        :content \"Look at this image\"
        :images [\"base64encodedimagedata\"]})
     ;; => true"
  {:role schema/Str
   :content schema/Str
   (schema/optional-key :images) [schema/Str]
   (schema/optional-key :tool_calls) [ToolCall]
  })

(schema/defschema ChatRequest
  "A request to generate a chat response from Ollama.

   WHAT: Defines the full structure of a chat completions request,
     including the model, message history, optional tools, and generation
     parameters.

   WHY: Used to validate a chat request before sending it to the API,
     ensuring all required fields are present and optional fields have
     correct types. This prevents cryptic API errors by catching
     malformed requests locally.

   ARGUMENTS: Not called directly — used by schema/validate in the
     chat function.

   RETURNS: Returns true if the provided map fits the schema, otherwise
     false. Throws an exception on validation failure.

   EXAMPLE:
     (schema/validate ChatRequest
       {:model \"llama3.1:latest\"
        :messages [{:role \"user\" :content \"Hello, how are you?\"}]})
     ;; => true

     (schema/validate ChatRequest
       {:model \"llama3.1:latest\"
        :messages
        [{:role \"system\" :content \"You are a helpful assistant.\"}
         {:role \"user\" :content \"Explain quantum computing in one sentence.\"}]
        :options {:temperature 0.7
                  :num_ctx 4096}})
     ;; => true"
  {:model schema/Str
   :messages [ChatMessage]
   :tools [Tool]
   (schema/optional-key :options) {(schema/optional-key :seed) schema/Int
                                   (schema/optional-key :temperature) schema/Num
                                   (schema/optional-key :num_ctx) schema/Int}
   (schema/optional-key :stream) schema/Bool
   (schema/optional-key :thinking) schema/Bool})

(schema/defschema ChatMessageResponse
  "A message in a chat response from Ollama.

   WHAT: Defines the structure of an assistant message returned from a
     chat completions response.

   WHY: Used to validate the messages returned by the API, ensuring
     they conform to the expected shape before returning them to the
     caller. Supports thinking (for models with chain-of-thought output),
     images (for vision models), and tool calls.

   ARGUMENTS: Not called directly — used by schema/validate internally.

   RETURNS: Returns true if the provided map conforms to the schema,
     otherwise false.

   EXAMPLE:
     (schema/validate ChatMessageResponse
       {:role \"assistant\"
        :content \"The capital of France is Paris.\"})
     ;; => true

     (schema/validate ChatMessageResponse
       {:role \"assistant\"
        :content \"Let me think about this...\"
        :thinking \"Paris is the capital because...\"})
     ;; => true"
  {:role schema/Str
   :content schema/Str
   (schema/optional-key :thinking) schema/Str
   (schema/optional-key :images) [schema/Str]
   (schema/optional-key :tool_calls) [ToolCall]
  })

(schema/defschema ChatResponse
  "A complete response from the /api/chat endpoint.

   WHAT: Defines the full structure of a chat completions response from
     Ollama, including the response messages and detailed timing
     / evaluation metrics.

   WHY: Used to validate the complete response from the chat function,
     ensuring timing metrics and response data are well-formed before
     returning to the caller. Also supports optional log-probabilities,
     done flags, and done reasons for streaming and completion detection.

   ARGUMENTS: Not called directly — used by schema/validate in the
     chat function.

   RETURNS: Returns true if the provided map conforms to the schema,
     otherwise false.

   EXAMPLE:
     (schema/validate ChatResponse
       {:model \"llama3.1:latest\"
        :created_at \"2024-01-15T10:30:00Z\"
        :messages [{:role \"assistant\"
                     :content \"Paris is the capital of France.\"}]
        :total_duration 25000000
        :load_duration 5000000
        :prompt_eval_count 12
        :prompt_eval_duration 2000000
        :eval_count 8
        :eval_duration 15000000})
     ;; => true"
  {:model schema/Str
   :created_at schema/Str
   :messages [ChatMessageResponse]
   :total_duration schema/Int
   :load_duration schema/Int
   :prompt_eval_count schema/Int
   :prompt_eval_duration schema/Int
   :eval_count schema/Int
   :eval_duration schema/Int
   (schema/optional-key :logprobs) {:token schema/Str
                                    :logprob schema/Num
                                    :bytes [schema/Int]
                                    :top_logprobs [{:token schema/Str
                                                     :logprob schema/Num
                                                     :bytes [schema/Int]}]}
   (schema/optional-key :done) schema/Bool
   (schema/optional-key :done_reason) schema/Str})

(defn chat
  "Generate a chat response from a given request.

   WHAT: Sends a chat completions request to the Ollama API and returns
     the validated response, including the model's reply.

   WHY: This is the primary function for getting responses from LLM models
     via Ollama. It supports multi-turn conversations, system prompts,
     tool calling, streaming, and model-specific options (temperature,
     seed, context window). The built-in validation ensures malformed
     requests are caught early and responses conform to the expected schema.

   ARGUMENTS:
     - request: A map conforming to `ChatRequest` schema, containing:
       - `:model` (string): The model to use, e.g. \"llama3.1:latest\".
       - `:messages` (vector of ChatMessage): The conversation messages.
       - `:tools` (optional vector of Tool): Tools available for the
         model to call.
       - `:options` (optional map): Model options like :seed, :temperature,
         :num_ctx.
       - `:stream` (optional bool): Whether to stream the response.
       - `:thinking` (optional bool): Whether to enable thinking mode.
     - options: A map conforming to `ClientOptions` schema, containing:
       - `:base-url` (optional string): Custom base URL for the Ollama server.
       - `:token` (optional string): Authentication token for the server.

   RETURNS: A map conforming to `ChatResponse` schema, with keys:
       - `:model`: The model used.
       - `:created_at`: ISO timestamp of creation.
       - `:messages`: Vector of response messages (usually one assistant message).
       - `:total_duration`: Total response time in nanoseconds.
       - `:load_duration`: Time to load the model in nanoseconds.
       - `:prompt_eval_count`: Number of prompt tokens evaluated.
       - `:prompt_eval_duration`: Time to evaluate prompt tokens.
       - `:eval_count`: Number of generated tokens.
       - `:eval_duration`: Time to generate tokens.
       - `:logprobs` (optional): Token-level log probabilities.
       - `:done` (optional): Whether generation is complete.
       - `:done_reason` (optional): Why generation ended.

   EXAMPLE:
     ;; Simple single-turn chat
     (chat {:model \"llama3.1:latest\"
            :messages [{:role \"user\" :content \"What is the capital of France?\"}]})
     ;; => {:model \"llama3.1:latest\"
     ;;     :created_at \"2024-01-15T10:30:00Z\"
     ;;     :messages [{:role \"assistant\" :content \"The capital of France is Paris.\"}]
     ;;     :total_duration 25000000
     ;;     :eval_count 12
     ;;     ...}

     ;; Multi-turn conversation with system prompt
     (chat {:model \"llama3.1:latest\"
            :messages [{:role \"system\" :content \"You are a helpful geography assistant.\"}
                       {:role \"user\" :content \"What is Paris known for?\"}
                       {:role \"assistant\" :content \"Paris is known for the Eiffel Tower.\"}
                       {:role \"user\" :content \"How long is the tower?\"}]})
     ;; => {:model \"llama3.1:latest\"
     ;;     :messages [{:role \"assistant\" :content \"The Eiffel Tower is approximately 324 meters tall.\"}]
     ;;     ...}

     ;; Using a custom server
     (chat {:model \"llama3.1:latest\"
            :messages [{:role \"user\" :content \"Tell me a joke.\"}]}
           {:base-url \"http://remote-server.example.com:11434\"})
     ;; => {:model \"llama3.1:latest\" :messages [...] ...}"
  ([request] (chat request {:base-url default-base-url}))
  ([request options]
   (let [body (schema/validate ChatRequest request)
         response (do-post "/api/chat" body options)]
     (schema/validate ChatResponse (<-json (:body response) :schema ChatResponse)))))
