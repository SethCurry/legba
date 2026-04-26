(ns legba.sql.entity-type
  (:require [legba.sql.core :refer [Model]]
            [legba.sql :refer [do-query]]
            [legba.mcp :refer [new-text-content]]))


(defrecord EntityType [id name description]
  Model
  (->llm-context [this]
     (new-text-content (str "ID: " (:id this)
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
