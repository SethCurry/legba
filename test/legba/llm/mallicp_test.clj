(ns legba.llm.mallicp-test
  (:require [clojure.test :refer [deftest is testing]]
            [legba.llm.mallicp :as mcp]))

(deftest schema-to-jsonschema-test
  (testing "empty schema"
    (is (= {:type "object", :properties {}, :required ()} (mcp/schema-to-jsonschema []))))
  (testing "single string field"
    (is (= {:type "object", :properties {:name {:type "string"}}, :required []} (mcp/schema-to-jsonschema [:map [:name :string]])))))
