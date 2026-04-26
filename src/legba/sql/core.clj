(ns legba.sql.core)

(defprotocol Model 
  (->llm-context [this]))