(ns legba.sql.relationship-type
  (:require [legba.sql.core :refer [Model do-query]]
            [legba.mcp :refer [new-text-content]]))


; id name bidirectional description
(defrecord RelationshipType [id name bidirectional description]
  Model
  (->llm-context [this]
    (new-text-content (str "ID: " (:id this)
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

(defn get-relationship-type-by-name [name]
  (first (do-query {:select [:*]
                    :from :relationship_types
                    :where [:= :name name]}
                   :unmarshaller unmarshal-relationship-type)))
