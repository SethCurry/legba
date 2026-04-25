(ns legba.json
  (:require [jsonista.core :as json]
            [schema.core :as s]))

(def mapper (json/object-mapper {:decode-key-fn keyword}))

(defn ->json
  "Converts an object to a JSON string using keywords as keys."
  [x & {:keys [schema]
        :or {schema nil}}]
  (when (not (nil? schema))
    (s/validate schema x))
 (json/write-value-as-string x mapper))

(defn <-json
  "Converts a JSON string to an object using keywords as keys."
  [x & {:keys [schema]
        :or {schema nil}}]
  (let [result (json/read-value x mapper)]
    (when (not (nil? schema))
      (s/validate schema result))
    result))