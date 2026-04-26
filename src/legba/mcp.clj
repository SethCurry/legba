(ns legba.mcp
  (:require [schema.core :as s]
            [legba.json :refer [->json <-json]]
            [ring.util.request :refer [body-string]]
            [taoensso.telemere :as t]))

(def protocol-version "2025-11-25")

(defn schema-type-to-jsonschema-type [schema-type]
  (t/log! {:level :debug
           :msg "schema-type-to-jsonschema-type"
           :data {:schema-type schema-type}})
  (let [str-schema-type (str schema-type)]
    (t/log! {:level :debug
             :msg "str-schema-type"
             :data {:str-schema-type str-schema-type}})
    (cond
      (identical? schema-type java.lang.String) "string"
      (identical? schema-type Boolean) "boolean"
      (identical? schema-type Number) "number"
      (identical? schema-type Integer) "integer"
      (identical? schema-type s/Int) "integer"
      :else (throw (ex-info (str "Unknown schema type: " (class schema-type)) {:schema-type str-schema-type})))))

(defn schema-to-jsonschema [schema]
  (let [description (or (:description schema) "")
        type (:type schema)]
    (cond
      (not (nil? type)) {:type (schema-type-to-jsonschema-type type)
                         :description description}
      (map? schema) {:type "object"
                     :properties (into {} (map (fn [x] {(first x) (schema-to-jsonschema (second x))}) schema))
                     :required (keys schema)}
      (vector? schema) {:type "array"
                        :items {:type "string"}}
      :else (throw (ex-info (str "Unknown schema: " schema) {:schema schema})))))

(defn schema-to-schema [schema]
  (let [type (:type schema)]
    (cond
      (not (nil? type)) type
      (map? schema) (into {} (map (fn [x] {(first x) (schema-to-schema (second x))}) schema))
      (vector? schema) (into [] (map schema-to-schema schema))
      :else (throw (ex-info (str "Unknown schema: " schema) {:schema schema})))))

(defprotocol PTool
  (call-tool [this req-id params])
  (mcp-schema [this]))

(defrecord Tool [handler mcp-info schema]
  PTool
  (call-tool [this req-id params] (handler req-id (s/validate schema params)))
  (mcp-schema [this] mcp-info))

(defn deftool [name title description handler raw-schema]
  (Tool. handler
         {:name name
          :title title
          :description description
          :inputSchema (schema-to-jsonschema raw-schema)}
         (schema-to-schema raw-schema)))

; Initialize request
;
; {
;  "jsonrpc": "2.0",
;  "id": 1,
;  "method": "initialize",
;  "params": 
;  {
;    "protocolVersion": "2025-06-18",
;    "capabilities":
;          {
;      "elicitation": {}
;    },
;    "clientInfo": {
;      "name": "example-client",
;      "version": "1.0.0"
;    }
;  }
;}
(s/defschema InitializeRequest
  "Initializes the MCP server."
  {:jsonrpc s/Str
   :id s/Int
   :method s/Str
   :params {s/Keyword s/Any}})

; {
;  "jsonrpc": "2.0",
;  "id": 1,
;  "result":  {
;    "protocolVersion": "2025-06-18",
;    "capabilities": {
;      "tools": {
;        "listChanged": true
;      },
;      "resources": {}
;    },
;    "serverInfo": {
;      "name": "example-server",
;      "version": "1.0.0"
;    }
;  }
;}
(s/defschema InitializeResponse
  "Initializes the MCP server."
  {:jsonrpc s/Str
   :id s/Int
   :result {s/Keyword s/Any}})

(s/defschema STool
  "A tool that can be used to perform an action."
  {:name s/Str
   :title s/Str
   :description s/Str
   :inputSchema {s/Str s/Any}})

(s/defschema ToolListRequest
  "A request to list tools."
  {:jsonrpc s/Str
   :id s/Int
   :method s/Str}) ; always "tools/list"

(s/defschema ToolListResponse
  "A response to a tool list request."
  {:jsonrpc s/Str
   :id s/Int
   :result {:result [STool]}})

(s/defschema ToolCallRequest
  "A request to call a tool."
  {:jsonrpc s/Str
   :id s/Int
   :method s/Str ; always "tools/call"
   :params {s/Keyword s/Any}})

(s/defschema ToolCallResponse
  "A response to a tool call."
  {:jsonrpc s/Str
   :id s/Int
   :result {s/Keyword s/Any}})

(defn list-tools-handler [req-id tools]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (->json {:jsonrpc "2.0"
                  :id req-id
                  :result {:tools (s/validate [STool] (doall (map #(mcp-schema %) tools)))}})})

(defn initialize-handler [req-id]
  (t/log! {:level :debug
           :msg "Got initialize request"})
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (->json {:jsonrpc "2.0"
                  :id req-id
                  :result {:protocolVersion protocol-version
                           :capabilities {:tools {:listChanged false}}
                           :serverInfo {:name "Legba"
                                        :title "Legba Knowledgebase"
                                        :version "0.0.1"
                                        :description "A knowledgebase for your agent"}}})})

(defn new-text-content [text]
  {:type "text"
   :text text})

(defn- find-tool [tools name]
  (let [tool (first (filter (fn [x] (= (:name (mcp-schema x)) name)) tools))]
    (if (not (nil? tool))
      tool
      (throw (ex-info "Tool not found" {:tool name})))))

; Handlers get req-id and args, and return a list of results
(defn tool-call-handler [req-id params tools]
  (let [tool-name (:name params)
        args (:arguments params)]
    (t/log! {:msg "Got tool call"
             :level :debug
             :data {:tool-name tool-name
                    :args args}})
    (let [tool (find-tool tools tool-name)
          result (call-tool tool req-id args)]
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (->json {:jsonrpc "2.0"
                      :id req-id
                      :result {:content result
                               :isError false}})})))

(defn handle-tool-call [req-id rpc tools]
  (let [validated-params (s/validate ToolCallRequest rpc)
        result (tool-call-handler req-id (:params validated-params) tools)]
    (t/log! {:level :debug
             :msg "Tool call result"
             :data {:result result}})
    result))

(defn router [tools]
  (fn [req]
    (let [body (body-string req)]
      (t/log! {:level :debug
               :msg "Got MCP request"
               :data {:body body}})
      (let [rpc (<-json body)
            method (:method rpc)
            req-id (:id rpc)]
        (case method
          "initialize" (initialize-handler req-id)
          "tools/list" (list-tools-handler req-id tools)
          "tools/call" (let [validated-params (s/validate ToolCallRequest rpc)
                             result (tool-call-handler req-id (:params rpc) tools)]
                         (t/log! {:level :debug
                                  :msg "Tool call result"
                                  :data {:result result}})
                         (tool-call-handler req-id (:params validated-params) tools))
          "resources/list" {:status 200
                            :body (->json {:jsonrpc "2.0"
                                           :id req-id
                                           :result {:resources []}})}
          "notifications/initialized" {:status 200
                                       :body (->json {:jsonrpc "2.0"
                                                      :id req-id
                                                      :result {:content "Initialized"}})}
          (throw (ex-info "Unknown method" {:method method})))))))