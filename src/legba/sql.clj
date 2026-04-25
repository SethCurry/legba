(ns legba.sql
  (:require [hikari-cp.core :refer [make-datasource]]
            [next.jdbc :as jdbc]
            [honey.sql :as sql]
            [taoensso.telemere :as t]
            [scurvy.json :refer [->json <-json]]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as rs]
            [legba.mcp :as mcp])
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
  (let [config {:username "legba" :password "legba" :database "legba" :hostname "localhost" :port 25654}]
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
           :username (:username config)
           :password (:password config)
           :database-name (:database config)
           :server-name (:hostname config)
           :port-number (:port config))))

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

(defprotocol ContextMarshaller
  (marshal-context [this]))

(defrecord EntityType [id name description]
  ContextMarshaller
  (marshal-context [this]
     (mcp/new-text-content (str "ID: " (:id this)
                                "\nName: " (:name this)
                                "\nDescription: " (:description this)))))

(defn- unmarshal-entity-type [x] (EntityType. (:entity_types/id x) (:entity_types/name x) (:entity_types/description x)))

(defn create-entity-type [name description]
  (do-query {:insert-into :entity_types
             :values [{:name name
                       :description description}]
             :returning :id}
            (fn [x] (EntityType. (:entity_types/id x) name description))))

(defn query-entity-types []
  (do-query {:select [:*]
             :from :entity_types}
            :unmarshaller unmarshal-entity-type))

(defn get-entity-type-by-name [name]
  (first (do-query {:select [:*]
                    :from :entity_types
                    :where [:= :name name]}
                   :unmarshaller unmarshal-entity-type)))

(defrecord Entity [id entity-type-id name attributes]
  ContextMarshaller
  (marshal-context [this]
    (mcp/new-text-content (str "ID: " (:id this)
                               "\nEntity Type: " (:entity-type-id this)
                               "\nName: " (:name this)
                               "\nAttributes: " (:attributes this)))))

(defn- unmarshal-entity [x]
  (Entity. (:entities/id x)
           (:entities/entity_type_id x)
           (:entities/name x)
           (:entities/attributes x)))

(defn create-entity [entity-type-id name attributes]
  (do-query {:insert-into :entities
             :values [{:entity_type_id entity-type-id
                       :name name
                       :attributes [:param :entity-attributes]}]
             :returning :id}
            :unmarshaller (fn [x] (Entity. (:entities/id x) entity-type-id name attributes))
            :opts {:params {:entity-attributes attributes}}))

(defn query-entities []
  (do-query {:select [:*]
             :from :entities}
            :unmarshaller unmarshal-entity))

; id name bidirectional description
(defrecord RelationshipType [id name bidirectional description]
  ContextMarshaller
  (marshal-context [this]
    (mcp/new-text-content (str "ID: " (:id this)
                               "\nName: " (:name this)
                               "\nBidirectional: " (:bidirectional this)
                               "\nDescription: " (:description this)))))

(defn- unmarshal-relationship-type [x] (RelationshipType.
                                        (:relationship_types/id x)
                                        (:relationship_types/name x)
                                        (:relationship_types/bidirectional x)
                                        (:relationship_types/description x)))

(defn create-relationship-type [name bidirectional description]
  (do-query {:insert-into :relationship_types
             :values [{:name name
                       :bidirectional bidirectional
                       :description description}]
             :returning :id}
            :unmarshaller (fn [x] (RelationshipType. (:relationship_types/id x) name bidirectional description))))

(defn query-relationship-types []
  (do-query {:select [:*]
             :from :relationship_types}
            :unmarshaller unmarshal-relationship-type))

; id relationship-type-id source-entity-id target-entity-id attributes
(defrecord Relationship [id relationship-type-id source-entity-id target-entity-id attributes]
  ContextMarshaller
  (marshal-context [this]
    (mcp/new-text-content (str "ID: " (:id this)
                               "\nRelationship Type: " (:relationship-type-id this)
                               "\nSource Entity ID: " (:source-entity-id this)
                               "\nTarget Entity ID: " (:target-entity-id this)
                               "\nAttributes: " (:attributes this)))))

(defn- unmarshal-relationship [x]
  (Relationship. (:relationships/id x)
                 (:relationships/relationship_type_id x)
                 (:relationships/source_entity_id x)
                 (:relationships/target_entity_id x)
                 (:relationships/attributes x)))

(defn create-relationship [relationship-type-id source-entity-id target-entity-id attributes]
  (do-query {:insert-into :relationships
             :values [{:relationship_type_id relationship-type-id
                       :source_entity_id source-entity-id
                       :target_entity_id target-entity-id
                       :attributes [:param :relationship-attributes]}]
             :returning :id}
            :unmarshaller (fn [x] (Relationship. (:relationships/id x) relationship-type-id source-entity-id target-entity-id attributes))
            :opts {:params {:relationship-attributes attributes}}))

(defn query-relationships []
  (do-query {:select [:*]
             :from :relationships}
            :unmarshaller unmarshal-relationship))

(defrecord Document [id name description content created-at updated-at entity-id]
  ContextMarshaller
  (marshal-context [this]
    (mcp/new-text-content (str "ID: " (:id this)
                               "\nName: " (:name this)
                               "\nDescription: " (:description this)
                               "\nCreated At: " (:created-at this)
                               "\nUpdated At: " (:updated-at this)
                               "\nEntity ID: " (:entity-id this)
                               "\nContent: " (:content this)))))

(defn- unmarshal-document [x] (Document. (:documents/id x)
                                         (:documents/name x)
                                         (:documents/description x)
                                         (:documents/content x)
                                         (:documents/created-at x)
                                         (:documents/updated-at x)
                                         (:documents/entity-id x)))

(defn create-document [name description content entity-id]
  (do-query {:insert-into :documents
             :values [{:name name
                       :description description
                       :content content
                       :entity_id entity-id}]
             :returning :id}
            :unmarshaller (fn [x] (Document. (:documents/id x) name description content (java.util.Date.) (java.util.Date.) entity-id))
            :opts {:params {:created-at (java.util.Date.)}}))

(defn query-documents []
  (do-query {:select [:*]
             :from :documents}
            :unmarshaller unmarshal-document))