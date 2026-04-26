(ns legba.tools
  (:require [legba.mcp :as mcp]
            [legba.sql :as sql]
            [schema.core :as s]
            [legba.sql.entity :as entity]
            [legba.sql.entity-type :as entity-type]
            [legba.sql.relationship-type :as relationship-type]
            [legba.sql.relationship :as relationship]
            [legba.sql.document :as document]))


(def create-entity-type-tool (mcp/deftool
                               "create-entity-type"
                               "Create Entity Type"
                               "Creates a new entity"
                               (fn [req-id params] (let [entity-type-name (:name params)
                                                         entity-type-description (:description params)
                                                         new-entity-type (entity-type/create-entity-type entity-type-name entity-type-description)]
                                                     [(mcp/new-text-content (str "Created entity type \"" entity-type-name "\" with ID " (:id new-entity-type)))]))
                               {:name {:type s/Str
                                       :description "The name of the entity type to create"}
                                :description {:type s/Str
                                              :description "The description of the entity type to create"}}))
(defn- entity-type-to-string [et]
  (str "ID: " (:id et)
       "\nName: " (:name et)
       "\nDescription: " (:description et)))

(def query-entity-types-tool (mcp/deftool
                               "query-entity-types"
                               "Query Entity Types"
                               "Query all entity types."
                               (fn [req-id params]
                                 (let [entity-types (entity-type/query-entity-types)]
                                   [(mcp/new-text-content (str "Found entity types: " (apply str (doall (map entity-type-to-string entity-types)))))]))
                               {}))

(def create-entity-tool (mcp/deftool
                          "create-entity"
                          "Create entity"
                          "Create a new entity to track a specific instance of an entity type such as a person, team, application, or anything else."
                          (fn [req-id params]
                            (let [entity-type-name (:entity-type params)
                                  found-entity-type (entity-type/get-entity-type-by-name entity-type-name)
                                  entity-type-id (:id found-entity-type)
                                  entity-name (:name params)
                                  entity-attributes-raw (:attributes params)
                                  entity-attributes (if (nil? entity-attributes-raw)
                                                      {}
                                                      entity-attributes-raw)
                                  new-entity (entity/create-entity entity-type-id entity-name entity-attributes)]
                              [(mcp/new-text-content (str "Created entity \"" entity-name "\" with ID " (:id new-entity)))]))
                          {:entity-type {:type s/Str
                                         :description "The name of the entity type to create the entity for"}
                           :name {:type s/Str
                                  :description "The name of the entity to create"}
                           :attributes {:type s/Str
                                        :description "The attributes of the entity to create"}}))

(defn- relationship-type-to-string [rt]
  (str "ID: " (:id rt)
       "\nName: " (:name rt)
       "\nBidirectional: " (:bidirectional rt)
       "\nDescription: " (:description rt)))

(def query-relationship-types-tool (mcp/deftool
                                     "query-relationship-types"
                                     "Query Relationship Types"
                                     "Query all relationship types."
                                     (fn [req-id params]
                                       (let [relationship-types (relationship-type/query-relationship-types)]
                                         [(mcp/new-text-content (str "Found relationship types: " (apply str (doall (map relationship-type-to-string relationship-types)))))]))
                                     {}))

(defn- relationship-to-string [rtsp]
  (str "ID: " (:id rtsp)
       "\nRelationship Type: " (:relationship-type-id rtsp)
       "\nSource Entity: " (:source-entity-id rtsp)
       "\nTarget Entity: " (:target-entity-id rtsp)
       "\nAttributes: " (:attributes rtsp)))

(def query-relationships-tool (mcp/deftool
                                "query-relationships"
                                "Query Relationships"
                                "Query all relationships."
                                (fn [req-id params]
                                  (let [relationships (relationship/query-relationships)]
                                    [(mcp/new-text-content (str "Found relationships: " (apply str (doall (map relationship-to-string relationships)))))]))
                                {}))

(def create-relationship-type-tool (mcp/deftool
                                     "create-relationship-type"
                                     "Create Relationship Type"
                                     "Create a new relationship type to track a specific relationship between two entities."
                                     (fn [req-id params]
                                       (let [relationship-type-name (:name params)
                                             relationship-type-bidirectional (:bidirectional params)
                                             relationship-type-description (:description params)
                                             new-relationship-type (relationship-type/create-relationship-type relationship-type-name relationship-type-bidirectional relationship-type-description)]
                                         [(mcp/new-text-content (str "Created relationship type \"" relationship-type-name "\" with ID " (:id new-relationship-type)))]))
                                     {:name {:type s/Str
                                             :description "The name of the relationship type to create"}
                                      :bidirectional {:type s/Bool
                                                      :description "Whether the relationship is bidirectional"}
                                      :description {:type s/Str
                                                    :description "The description of the relationship type to create"}}))


(def create-relationship-tool (mcp/deftool
                                "create-relationship"
                                "Create Relationship"
                                "Create a new relationship between two entities."
                                (fn [req-id params]
                                  (let [relationship-type-id (:relationship-type-id params)
                                        source-entity-id (:source-entity-id params)
                                        target-entity-id (:target-entity-id params)
                                        relationship-attributes-raw (:attributes params)
                                        relationship-attributes (if (nil? relationship-attributes-raw)
                                                                  {}
                                                                  relationship-attributes-raw)
                                        new-relationship (relationship/create-relationship relationship-type-id source-entity-id target-entity-id relationship-attributes)]
                                    [(mcp/new-text-content (str "Created relationship with ID " (:id new-relationship)))]))
                                {:relationship-type-id {:type s/Int
                                                        :description "The ID of the relationship type to create the relationship for"}
                                 :source-entity-id {:type s/Int
                                                    :description "The ID of the source entity to create the relationship for"}
                                 :target-entity-id {:type s/Int
                                                    :description "The ID of the target entity to create the relationship for"}
                                 :attributes {:type s/Str
                                              :description "The attributes of the relationship to create"}}))
