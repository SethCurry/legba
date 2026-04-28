(ns legba.sql.core
  (:require [hikari-cp.core :refer [make-datasource]]
            [next.jdbc :as jdbc]
            [honey.sql :as sql]
            [taoensso.telemere :as t]
            [legba.json :refer [->json <-json]]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as rs]
            [legba.config :as config])
  (:import [org.postgresql.util PGobject]
           [java.sql PreparedStatement]))


(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x)) "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (->json x)))))

(defn <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure data."
  [^PGobject v]
  (let [type  (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (some-> value <-json (with-meta {:pgtype type}))
      value)))

;; if a SQL parameter is a Clojure hash map or vector, it'll be transformed
;; to a PGobject for JSON/JSONB:
(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m)))

  clojure.lang.IPersistentVector
  (set-parameter [v ^PreparedStatement s i]
    (.setObject s i (->pgobject v))))

;; if a row contains a PGobject then we'll convert them to Clojure data
;; while reading (if column is either "json" or "jsonb" type):
(extend-protocol rs/ReadableColumn
  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (<-pgobject v)))

(def datasource-options
  (let [cfg @config/loaded-config
        sql-config (:database cfg)]
    (assoc {:auto-commit        true
            :read-only          false
            :connection-timeout 30000
            :validation-timeout 5000
            :idle-timeout       600000
            :max-lifetime       1800000
            :minimum-idle       1
            :maximum-pool-size  10
            :pool-name          "legba-db-pool"
            :adapter            "postgresql"
            :register-mbeans    false}
           :username (:username sql-config)
           :password (:password sql-config)
           :database-name (:database sql-config)
           :server-name (:hostname sql-config)
           :port-number (:port sql-config))))

(defonce datasource
  (delay (make-datasource datasource-options)))

(defn raw-query [query & {:keys [unmarshaller]
                          :or {unmarshaller nil}}]
  (t/log! {:level :debug :msg "Executing query" :data {:query (first query) :params (rest query)}})
  (let [rows (jdbc/execute! @datasource query)]
    (if (not (nil? unmarshaller))
      (map unmarshaller rows)
      rows)))

(defn do-query
  "Executes a query and returns the rows.
  
  Args:
  - query (map): The query to execute.
  
  Keyword Arguments:
   - :unmarshaller (function): A function to unmarshal the rows.
   - :opts (map): Options to pass to the sql/format function.
     - :params (map): Parameters to pass to the sql/format function.

  Returns:
  - The rows, or unmarshalled rows if :unmarshaller is provided.
  "
  [query & {:keys [unmarshaller opts]
            :or {unmarshaller nil
                 opts {}}}]
  (let [formatted-query (sql/format query opts)]
    (t/log! {:level :debug :msg "Executing query" :data {:query formatted-query}})
    (raw-query formatted-query :unmarshaller unmarshaller)))

(defprotocol Model 
  (->llm-context [this])
  (->cli [this]))