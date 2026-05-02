(ns legba.sql.entity
  (:require [legba.sql.core :refer [Model do-query ->cli]]
            [legba.mcp :refer [new-text-content]]))

;; A record representing an Entity — a concrete instance of an Entity Type
;; within the knowledge graph. Each Entity belongs to exactly one Entity Type
;; (e.g., a "Person" named "Alice" is an Entity of Entity Type "Person").
;;
;; Fields:
;;   :id              - The unique database identifier for the entity.
;;   :entity-type-id  - The numeric ID of the Entity Type this entity
;;                      belongs to (e.g., 3 for "Person").
;;   :name            - A human-readable name for this entity
;;                      (e.g., "Alice", "Project X").
;;   :attributes      - A JSONB map (Clojure map) of arbitrary
;;                      key-value metadata (e.g., {:age 30, :email "alice@example.com"}).
(defrecord Entity [id entity-type-id name attributes]
  Model

  ;; Converts this Entity into a human-readable CLI string.
  ;;
  ;; Why use it:
  ;;   To display the entity in command-line output (e.g., log messages,
  ;;   REPL inspections, CLI tools).
  ;;
  ;; Arguments: None (called on the Entity instance itself, `this`).
  ;;
  ;; Returns: A String with each field on its own line, formatted as:
  ;;
  ;;   ID: <id>
  ;;   Entity Type: <entity-type-id>
  ;;   Name: <name>
  ;;   Attributes: <attributes>
  ;;
  ;; Example:
  ;;   (->cli (map->Entity {:id 1 :entity-type-id 3 :name "Alice" :attributes {:age 30}}))
  ;;   ;; => "ID: 1\nEntity Type: 3\nName: Alice\nAttributes: {:age 30}"
  (->cli [this]
    (str "ID: " (:id this)
                               "\nEntity Type: " (:entity-type-id this)
                               "\nName: " (:name this)
                               "\nAttributes: " (:attributes this)))

  ;; Converts this Entity into an LLM-compatible context object.
  ;;
  ;; Why use it:
  ;;   Used internally by the MCP resource layer so that when an LLM
  ;;   requests an entity, the response is in a format the LLM can
  ;;   consume (an `:text-content` MCP content item).
  ;;
  ;; Arguments: None (called on the Entity instance itself, `this`).
  ;;
  ;; Returns: A text-content map returned by `new-text-content`, which
  ;;          wraps the CLI string representation.
  ;;
  ;; Example:
  ;;   (->llm-context my-entity)
  ;;   ;; => {:type :text, :text "ID: 1\nEntity Type: 3\nName: Alice\n..."}
  (->llm-context [this]
    (new-text-content (->cli this))))

;; Converts a raw database result row into an Entity record.
;;
;; Why use it:
;;   Used internally by query functions (e.g., `query-entities`) as the
;;   `:unmarshaller` callback. When next.jdbc returns rows from the database,
;;   they are plain maps with namespace-qualified keys (e.g., `:entities/id`).
;;   This function transforms each row into a proper `Entity` record so
;;   the rest of the application can work with typed, well-structured data.
;;
;; Arguments:
;;   x - A map representing a single row from the `entities` table, with
;;       namespace-qualified keys: `:entities/id`,
;;       `:entities/entity_type_id`, `:entities/name`,
;;       `:entities/attributes`.
;;
;; Returns: An `Entity` record populated from the row.
;;
;; Example:
;;   (unmarshal-entity {:entities/id 1
;;                      :entities/entity_type_id 3
;;                      :entities/name "Alice"
;;                      :entities/attributes {:age 30}})
;;   ;; => #legba.sql.entity.Entity{:id 1, :entity-type-id 3,
;;   ;;      :name "Alice", :attributes {:age 30}}
(defn- unmarshal-entity [x]
  (Entity. (:entities/id x)
           (:entities/entity_type_id x)
           (:entities/name x)
           (:entities/attributes x)))

;; Creates a new Entity of the given Entity Type and persists it to the database.
;;
;; Why use it:
;;   Call this when you want to add a new concrete instance to the knowledge
;;   graph. For example, an LLM might create a "Person" entity for a new user,
;;   a "Project" entity for a new initiative, or a "Company" entity for a
;;   newly discovered organization.
;;
;;   The `attributes` map is stored as JSONB, so you can attach arbitrary
;;   structured metadata without modifying the schema (e.g., `{:age 30,
;;   :email "alice@example.com"}` for a Person).
;;
;; Arguments:
;;   entity-type-id - The numeric ID of the Entity Type this entity belongs
;;                    to (Integer / Long).  Must reference a valid row in
;;                    the `entity_types` table.
;;   name           - A human-readable name for the entity (String).
;;   attributes     - A Clojure map of arbitrary key-value metadata to store
;;                    as JSONB (Map).
;;
;; Returns: An `Entity` record representing the newly inserted row,
;;          with `:id` populated from the database.
;;
;; Example:
;;   (create-entity 3 "Alice" {:age 30 :email "alice@example.com"})
;;   ;; => #legba.sql.entity.Entity{:id 5, :entity-type-id 3,
;;   ;;      :name "Alice", :attributes {:age 30, :email "alice@example.com"}}
(defn create-entity [entity-type-id name attributes]
  (first
    (do-query {:insert-into :entities
               :values [{:entity_type_id entity-type-id
                         :name name
                         :attributes [:param :entity-attributes]}]
               :returning :id}
              :unmarshaller (fn [x]
                              (Entity. (:entities/id x)
                                       entity-type-id
                                       name
                                       attributes))
              :opts {:params {:entity-attributes attributes}})))

;; Retrieves all Entities from the database.
;;
;; Why use it:
;;   Call this when you need a full listing of every entity stored in the
;;   system. This is primarily used by the MCP resource layer to expose the
;;   complete entities index to the LLM, so the LLM can see what entities
;;   exist and then drill into specific ones by ID.
;;
;;   NOTE: This function has no filtering or pagination. If the entities
;;   table grows large, consider adding limit/offset or query parameters.
;;
;; Arguments: None.
;;
;; Returns: A (possibly empty) sequence of `Entity` records, one for each
;;          row in the `entities` table.
;;
;; Example:
;;   (query-entities)
;;   ;; => (#legba.sql.entity.Entity{:id 1, :entity-type-id 3, :name "Alice", ...}
;;   ;;     #legba.sql.entity.Entity{:id 2, :entity-type-id 4, :name "Project X", ...})
(defn query-entities []
  (do-query {:select [:*]
             :from :entities}
            :unmarshaller unmarshal-entity))
