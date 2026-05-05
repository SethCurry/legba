(ns legba.llm.providers.ollama-test
  (:require [clojure.test :refer [deftest is testing]]
            [legba.llm.providers.ollama :as ollama]
            [malli.core :as malli]))

(deftest ClientOptions-test
  (testing "empty ClientOptions passes"
    (is (true? (malli/validate ollama/ClientOptions {})))))