(ns legba.mcp-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json]
            [schema.core :as s]
            [legba.mcp :as mcp]))

(defn- json-req [body-map]
  {:body (java.io.ByteArrayInputStream.
          (.getBytes (json/write-value-as-string body-map json/keyword-keys-object-mapper)
                     "UTF-8"))})

(defn- parse-resp-body [resp]
  (json/read-value (:body resp) json/keyword-keys-object-mapper))

(deftest schema-type-to-jsonschema-type-test
  (testing "maps JVM and Schema types to JSON Schema type strings"
    (is (= "string" (mcp/schema-type-to-jsonschema-type java.lang.String)))
    (is (= "boolean" (mcp/schema-type-to-jsonschema-type Boolean)))
    (is (= "number" (mcp/schema-type-to-jsonschema-type Number)))
    (is (= "integer" (mcp/schema-type-to-jsonschema-type Integer)))
    (is (= "integer" (mcp/schema-type-to-jsonschema-type s/Int))))
  (testing "rejects unknown schema types"
    (is (thrown? Exception (mcp/schema-type-to-jsonschema-type java.util.Date)))))

(deftest schema-to-jsonschema-test
  (testing "typed leaf schema"
    (is (= {:type "string" :description "A field"}
           (mcp/schema-to-jsonschema {:type s/Str :description "A field"}))))
  (testing "object schema from map of fields"
    (is (= {:type "object"
            :properties {:msg {:type "string" :description "message"}}
            :required [:msg]}
           (mcp/schema-to-jsonschema
            {:msg {:type s/Str :description "message"}}))))
  (testing "array schema uses string items"
    (is (= {:type "array" :items {:type "string"}}
           (mcp/schema-to-jsonschema [{:type s/Str :description "x"}]))))
  (testing "rejects unknown schema shapes"
    (is (thrown? Exception (mcp/schema-to-jsonschema :not-a-schema)))))

(deftest schema-to-schema-test
  (testing "typed leaf returns Schema type"
    (is (= s/Str (mcp/schema-to-schema {:type s/Str :description "d"}))))
  (testing "map becomes nested schema map"
    (is (= {:msg s/Str :n s/Int}
           (mcp/schema-to-schema
            {:msg {:type s/Str :description "m"}
             :n {:type s/Int :description "n"}}))))
  (testing "vector becomes vector of inner schemas"
    (is (= [s/Str s/Int]
           (mcp/schema-to-schema
            [{:type s/Str :description "a"}
             {:type s/Int :description "b"}]))))
  (testing "rejects unknown schema shapes"
    (is (thrown? Exception (mcp/schema-to-schema 42)))))

(deftest deftool-and-call-tool-test
  (let [tool (mcp/deftool "echo" "Echo" "Echoes a message"
                          (fn [_req-id params]
                            [(mcp/new-text-content (str (:msg params)))])
                          {:msg {:type s/Str :description "message"}})]
    (testing "mcp-schema exposes tool metadata"
      (is (= "echo" (:name (mcp/mcp-schema tool))))
      (is (= "Echo" (:title (mcp/mcp-schema tool))))
      (is (= "Echoes a message" (:description (mcp/mcp-schema tool))))
      (is (= {:type "object"
              :properties {:msg {:type "string" :description "message"}}
              :required [:msg]}
             (:inputSchema (mcp/mcp-schema tool)))))
    (testing "call-tool invokes handler with validated params"
      (is (= [{:type "text" :text "hello"}]
             (mcp/call-tool tool 7 {:msg "hello"}))))
    (testing "invalid params fail Schema validation"
      (is (thrown? Exception (mcp/call-tool tool 1 {:msg 123}))))))

(deftest new-text-content-test
  (is (= {:type "text" :text "hi"} (mcp/new-text-content "hi"))))

(deftest list-tools-handler-test
  (let [t1 (mcp/deftool "a" "A" "d1" (fn [_ _] []) {:x {:type s/Str :description ""}})
        t2 (mcp/deftool "b" "B" "d2" (fn [_ _] []) {:y {:type s/Int :description ""}})
        resp (mcp/list-tools-handler 99 [t1 t2])
        body (parse-resp-body resp)]
    (is (= 200 (:status resp)))
    (is (= "application/json" (get-in resp [:headers "Content-Type"])))
    (is (= "2.0" (:jsonrpc body)))
    (is (= 99 (:id body)))
    (is (= 2 (count (get-in body [:result :tools]))))
    (is (= #{"a" "b"} (set (map :name (:tools (:result body))))))))

(deftest initialize-handler-test
  (let [resp (mcp/initialize-handler 1)
        body (parse-resp-body resp)]
    (is (= 200 (:status resp)))
    (is (= "application/json" (get-in resp [:headers "Content-Type"])))
    (is (= "2.0" (:jsonrpc body)))
    (is (= 1 (:id body)))
    (is (= "2025-11-25" (get-in body [:result :protocolVersion])))
    (is (= false (get-in body [:result :capabilities :tools :listChanged])))
    (is (= "Scurvy" (get-in body [:result :serverInfo :name])))))

(deftest tool-call-handler-test
  (let [tool (mcp/deftool "double" "Double" "doubles n"
                          (fn [_req-id params]
                            [(mcp/new-text-content (str (* 2 (:n params))))])
                          {:n {:type s/Int :description "n"}})
        resp (mcp/tool-call-handler 3 {:name "double" :arguments {:n 21}} [tool])
        body (parse-resp-body resp)]
    (is (= 200 (:status resp)))
    (is (= "2.0" (:jsonrpc body)))
    (is (= 3 (:id body)))
    (is (= false (:isError (:result body))))
    (is (= [{:type "text" :text "42"}] (:content (:result body)))))
  (testing "missing tool throws"
    (is (thrown? Exception
                 (mcp/tool-call-handler 1 {:name "nope" :arguments {}} [])))))

(deftest router-test
  (let [tool (mcp/deftool "ping" "Ping" "pong"
                          (fn [_ _] [(mcp/new-text-content "pong")])
                          {})
        h (mcp/router [tool])]
    (testing "initialize"
      (let [resp (h (json-req {:jsonrpc "2.0" :id 10 :method "initialize" :params {}}))
            body (parse-resp-body resp)]
        (is (= 200 (:status resp)))
        (is (= 10 (:id body)))
        (is (string? (get-in body [:result :protocolVersion])))))
    (testing "tools/list"
      (let [resp (h (json-req {:jsonrpc "2.0" :id 11 :method "tools/list"}))
            body (parse-resp-body resp)]
        (is (= 200 (:status resp)))
        (is (= "ping" (get-in body [:result :tools 0 :name])))))
    (testing "tools/call"
      (let [resp (h (json-req {:jsonrpc "2.0"
                               :id 12
                               :method "tools/call"
                               :params {:name "ping" :arguments {}}}))
            body (parse-resp-body resp)]
        (is (= 200 (:status resp)))
        (is (= [{:type "text" :text "pong"}] (:content (:result body))))))
    (testing "resources/list"
      (let [resp (h (json-req {:jsonrpc "2.0" :id 13 :method "resources/list"}))
            body (parse-resp-body resp)]
        (is (= 200 (:status resp)))
        (is (= [] (get-in body [:result :resources])))))
    (testing "notifications/initialized"
      (let [resp (h (json-req {:jsonrpc "2.0" :id 14 :method "notifications/initialized"}))
            body (parse-resp-body resp)]
        (is (= 200 (:status resp)))
        (is (= "Initialized" (get-in body [:result :content])))))
    (testing "unknown method throws"
      (is (thrown? Exception
                   (h (json-req {:jsonrpc "2.0" :id 15 :method "unknown/method"})))))))
