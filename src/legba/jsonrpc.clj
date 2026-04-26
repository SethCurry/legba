(ns legba.jsonrpc
  (:require [legba.json :refer [->json]]
            [schema.core :as s]))

(def version "2.0")

(defn ->jsonrpc [req-id results]
  (->json {:jsonrpc version
           :id req-id
           :result results}))

(defn ->jsonrpc-error [req-id error]
  (->json {:jsonrpc version
           :id req-id
           :error error}))

(defn jsonrpc-schema [schema]
  (->json {:jsonrpc version
           :id s/Int
           :result schema}))