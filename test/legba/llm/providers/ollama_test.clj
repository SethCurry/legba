(ns legba.llm.providers.ollama-test
  (:require [clojure.test :refer [deftest is testing]]
            [legba.llm.providers.ollama :as ollama]
            [malli.core :as malli]))

(deftest ClientOptions-test
  (testing "empty ClientOptions passes"
    (is (true? (malli/validate ollama/ClientOptions {}))))
  (testing "only base-url passes"
    (is (true? (malli/validate ollama/ClientOptions {:base-url "http://localhost:11434"}))))
  (testing "only token passes"
    (is (true? (malli/validate ollama/ClientOptions {:token "abc123"}))))
  (testing "base-url and token passes"
    (is (true? (malli/validate ollama/ClientOptions {:base-url "http://localhost:11434" :token "abc123"}))))
  (testing "invalid base-url fails"
    (is (false? (malli/validate ollama/ClientOptions {:base-url 1234}))))
  (testing "invalid token fails"
    (is (false? (malli/validate ollama/ClientOptions {:token 1234})))))

(deftest Model-test
  (let [valid-model {:model "llama3.1:latest"
                     :name "llama3.1:latest"
                     :modified_at "2024-01-15T10:30:00Z"
                     :size 4547000000
                     :digest "abc123def456"
                     :details {:format "gguf"
                               :family "llama"
                               :families ["llama"]
                               :parameter_size "8B"
                               :quantization_level "Q4_0"}}]
    (testing "fully valid model with all fields passes"
      (is (true? (malli/validate ollama/Model valid-model))))
    (testing "model missing optional remote_model and remote_host passes"
      (is (true? (malli/validate ollama/Model
                                 (dissoc valid-model :remote_model :remote_host)))))
    (testing "model missing required :model key fails"
      (is (false? (malli/validate ollama/Model (dissoc valid-model :model)))))
    (testing "model with wrong type for :size fails"
      (is (false? (malli/validate ollama/Model
                                  (assoc valid-model :size "4.5 GB")))))
    (testing "model with wrong type for :name fails"
      (is (false? (malli/validate ollama/Model
                                  (assoc valid-model :name 42)))))
    (testing "model with non-string in :details :families vector fails"
      (is (false? (malli/validate ollama/Model
                                  (assoc-in valid-model [:details :families] ["llama" 123])))))
    (testing "model missing :details nested key fails"
      (is (false? (malli/validate ollama/Model (dissoc valid-model :details)))))
    (testing "model where :details map has invalid type for :format fails"
      (is (false? (malli/validate ollama/Model
                                  (assoc-in valid-model [:details :format] 999)))))))
