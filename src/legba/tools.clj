(ns legba.tools
  (:require [legba.mcp :as mcp]
            [schema.core :as s]
            [legba.sql.core :refer [->llm-context]]
            [legba.sql.entity :as entity]
            [legba.sql.entity-type :as entity-type]
            [legba.sql.relationship-type :as relationship-type]
            [legba.sql.relationship :as relationship]))

(def create-entity-type-tool (mcp/deftool
                               "create-entity-type"
                               "Create Entity Type"
                               "Creates a new entity"
                               (fn [_ params] (let [entity-type-name (:name params)
                                                         entity-type-description (:description params)
                                                         new-entity-type (entity-type/create-entity-type entity-type-name entity-type-description)]
                                                     [(mcp/new-text-content (str "Created entity type \"" entity-type-name "\" with ID " (:id new-entity-type)))]))
                               {:name {:type s/Str
                                       :description "The name of the entity type to create"}
                                :description {:type s/Str
                                              :description "The description of the entity type to create"}}))

(def query-entity-types-tool (mcp/deftool
                               "query-entity-types"
                               "Query Entity Types"
                               "Query all entity types."
                               (fn [_ _]
                                 (let [entity-types (entity-type/query-entity-types)]
                                   [(doall (map ->llm-context entity-types))]))
                               {}))

(def create-entity-tool (mcp/deftool
                          "create-entity"
                          "Create entity"
                          "Create a new entity to track a specific instance of an entity type such as a person, team, application, or anything else."
                          (fn [_ params]
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
                           :attributes {:type [{:name {:type s/Str
                                                    :description "The name of the attribute"}
                                                :value {:type s/Str
                                                        :description "The value of the attribute"}}]
                                        :description "The attributes of the entity to create"}}))

(def query-relationship-types-tool (mcp/deftool
                                     "query-relationship-types"
                                     "Query Relationship Types"
                                     "Query all relationship types."
                                     (fn [_ _]
                                       (let [relationship-types (relationship-type/query-relationship-types)]
                                         [(doall (map ->llm-context relationship-types))]))
                                     {}))


(def query-relationships-tool (mcp/deftool
                                "query-relationships"
                                "Query Relationships"
                                "Query all relationships."
                                (fn [_ _]
                                  (let [relationships (relationship/query-relationships)]
                                    [(doall (map ->llm-context relationships))]))
                                {}))

(def create-relationship-type-tool (mcp/deftool
                                     "create-relationship-type"
                                     "Create Relationship Type"
                                     "Create a new relationship type to track a specific relationship between two entities."
                                     (fn [_ params]
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
                                (fn [_ params]
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
