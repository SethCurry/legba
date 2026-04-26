(ns legba.sql.relationship
  (:require [legba.sql.core :refer [Model do-query]]
            [legba.mcp :refer [new-text-content]]))


; id relationship-type-id source-entity-id target-entity-id attributes
(defrecord Relationship [id relationship-type-id source-entity-id target-entity-id attributes]
  Model
  (->llm-context [this]
    (new-text-content (str "ID: " (:id this)
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
