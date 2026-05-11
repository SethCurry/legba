(ns legba.llm.mallicp
  (:require
   [legba.json :refer [->json <-json]]
   [ring.util.request :refer [body-string]]
   [taoensso.telemere :as t]
   [malli.core :as malli]))

(def protocol-version "2025-11-25")

(defn schema-type-to-jsonschema-type [schema-type]
  (case schema-type
    :string "string"
    :boolean "boolean"
    (throw (ex-info "Unknown schema type" {:schema-type schema-type}))))

(defn- schema-item-to-jsonschema [schema-item]
  (let [key-name (first schema-item)
        item-options-or-type (second schema-item)
        has-options (map? item-options-or-type)]
    (if has-options
      (let [description (:description item-options-or-type)
            optional (:optional item-options-or-type)
            item-type (get schema-item 2)]
        {key-name {:type (schema-type-to-jsonschema-type item-type)
                   :description description
                   :optional optional}})
      {key-name {:type (schema-type-to-jsonschema-type item-options-or-type)}})))

(defn schema-to-jsonschema [items]
  (println items)
  (let [schema-items (into {}
                           (map
                            schema-item-to-jsonschema
                            (rest items)))]
    {:type "object"
     :properties (into {} (map (fn [x] [(first x) (dissoc (second x) :optional)]) schema-items))
     :required (map (fn [x] (first x))
                    (filter (fn [x] (true? (:optional (second x))))
                            schema-items))}))

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
                       :result {:tools (doall (map #(schema-to-jsonschema %) tools))}}]
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
  (let [tool (first (filter (fn [x] (= (:name x) name)) tools))]
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
          result ((:handler tool) req-id args)]
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
