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