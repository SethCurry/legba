(ns legba.sql.document
  (:require [legba.sql.core :refer [Model do-query ->cli]]
            [legba.mcp :refer [new-text-content]]))

(defrecord Document [id name description content created-at updated-at entity-id]
  Model
  (->cli [this]
    (str "ID: " (:id this)
                               "\nName: " (:name this)
                               "\nDescription: " (:description this)
                               "\nCreated At: " (:created-at this)
                               "\nUpdated At: " (:updated-at this)
                               "\nEntity ID: " (:entity-id this)
                               "\nContent: " (:content this)))
  (->llm-context [this]
    (new-text-content (->cli this))))

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