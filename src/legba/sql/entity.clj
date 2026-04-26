(ns legba.sql.entity
  (:require [legba.sql.core :refer [Model ->llm-context]]
            [legba.sql :refer [do-query]]
            [legba.mcp :refer [new-text-content]]))

(defrecord Entity [id entity-type-id name attributes]
  Model
  (->llm-context [this]
    (new-text-content (str "ID: " (:id this)
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
