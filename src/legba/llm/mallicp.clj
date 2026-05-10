(ns legba.llm.mallicp
  (:require [schema.core :as s]
            [legba.json :refer [->json <-json]]
            [ring.util.request :refer [body-string]]
            [taoensso.telemere :as t]
            [malli.core :as malli]))

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
      (not (nil? type)) (if (vector? type)
                          {:type "array"
                           :items {:type (schema-to-jsonschema (first type))}
                           :description description}
                          {:type (schema-type-to-jsonschema-type type)
                           :description description})
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
  (call-tool [_ req-id params] (handler req-id (s/validate schema params)))
  (mcp-schema [_] mcp-info))

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
(def IconSchema
  [:map
   [:src :string]
   [:mimeType {:optional true} :string]
   [:theme {:optional true} :string]
   [:sizes {:optional true} [:vector :string]]])

(def ImplementationSchema
  [:map
   [:icons {:optional false} [:vector IconSchema]]
   [:name :string]
   [:title {:optional false} :string]
   [:version :string]
   [:description {:optional false} :string]
   [:websiteUrl {:optional false} :string]])

(def InitializeRequestSchema
  [:map
   [:jsonrpc :string]
   [:id :int]
   [:method :string]
   [:params [:map
             [:protocolVersion :string]
             [:clientInfo ImplementationSchema]]]])

(def InitializeResponse
  [:map
   [:protocolVersion :string]
   [:serverInfo ImplementationSchema]
   [:capabilities [:map
                   [:prompts {:optional true} [:map
                                               [:listChanged {:optional true} :boolean]]]
                   [:resources {:optional true} [:map
                                                 [:listChanged {:optional true} :boolean]
                                                 [:subscribe {:optional true} :boolean]]]
                   [:tasks {:optional true}
                    [:list {:optional true} :map]
                    [:cancel {:optional true} :map]
                    [:requests {:optional true} [:map
                                                 [:tools {:optional true} [:map
                                                                           [:call {:optional true} :map]]]]]]
                   [:tools {:optional true} [:map
                                             [:listChanged {:optional true} :boolean]]]]]
   [:instructions {:optional true} :string]])

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

(def ToolSchema
  [:map
   [:name :string]
   [:title :string]
   [:description :string]
   [:inputSchema [:map [::malli/default [:map-of :string :any]]]]])

(def ListToolsRequest
  [:map
   [:jsonrpc :string]
   [:id :int]
   [:method :string]]) ; :method is always tools/list

(def ListToolsResponse
  [:map
   [:jsonrpc :string]
   [:id :int]
   [:result [:map
             [:tools [:vector ToolSchema]]]]])

(def CallToolRequest
  [:map
   [:jsonrpc :string]
   [:id :int]
   [:method :string]
   [:params [:map [::malli/default [:map-of :string :any]]]]])

(def CallToolResponse
  [:map
   [:jsonrpc :string]
   [:id :int]
   [:result [:map [::malli/default [:map-of :string :any]]]]])

(defn- validate-or-throw [schema body]
  (let [is-valid (malli/validate schema body)]
    (when-not is-valid
      (throw (ex-info "Invalid response" {:body body :errors (malli/explain schema body)})))))

(defn list-tools-handler [req-id tools]
  (let [response-body {:jsonrpc "2.0"
                       :id req-id
                       :result {:tools (doall (map #(mcp-schema %) tools))}}]
    (validate-or-throw ListToolsResponse response-body)
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (->json response-body)}))

(defn initialize-handler [req-id]
  (t/log! {:level :debug
           :msg "Got initialize request"})
  (let [response-body {:jsonrpc "2.0"
                       :id req-id
                       :result {:protocolVersion protocol-version
                                :capabilities {:tools {:listChanged false}}
                                :serverInfo {:name "Legba"
                                             :title "Legba Knowledgebase"
                                             :version "0.0.1"
                                             :description "A knowledgebase for your agent"}}}]
    (validate-or-throw InitializeResponse response-body)
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (->json response-body)}))

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
  (validate-or-throw CallToolRequest rpc)
  (let [result (tool-call-handler req-id (:params rpc) tools)]
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
          "tools/call" (do
                         (validate-or-throw CallToolRequest rpc)
                         (let [result (tool-call-handler req-id (:params rpc) tools)]
                           (t/log! {:level :debug
                                    :msg "Tool call result"
                                    :data {:result result}})
                           (tool-call-handler req-id (:params rpc) tools)))
          "resources/list" {:status 200
                            :body (->json {:jsonrpc "2.0"
                                           :id req-id
                                           :result {:resources []}})}
          "notifications/initialized" {:status 200
                                       :body (->json {:jsonrpc "2.0"
                                                      :id req-id
                                                      :result {:content "Initialized"}})}
          (throw (ex-info "Unknown method" {:method method})))))))
