(ns legba.sql.document
  (:require [legba.sql.core :refer [Model do-query ->cli]]
            [legba.mcp :refer [new-text-content]]))

;; A record representing a Document — a text-based note or piece of content
;; attached to a specific Entity in the knowledge graph.
;;
;; Fields:
;;   :id          - The unique database identifier for the document.
;;   :name        - A short, human-readable name for the document.
;;   :content     - The full body/text of the document.
;;   :created-at  - The Date/DateTime when the document was first created.
;;   :updated-at  - The Date/DateTime when the document was last modified.
;;   :entity-id   - The ID of the Entity this document is associated with.
(defrecord Document [id name content created-at updated-at entity-id]
  Model

  ;; Converts this Document into a human-readable CLI string.
  ;;
  ;; Why use it:
  ;;   To display the document in command-line output (e.g., log messages,
  ;;   REPL inspections, CLI tools).
  ;;
  ;; Arguments: None (called on the Document instance itself, `this`).
  ;;
  ;; Returns: A String with each field on its own line, formatted as:
  ;;
  ;;   ID: <id>
  ;;   Name: <name>
  ;;   Description: <description>
  ;;   Created At: <created-at>
  ;;   Updated At: <updated-at>
  ;;   Entity ID: <entity-id>
  ;;   Content: <content>
  ;;
  ;; Example:
  ;;   (->cli (map->Document {:id 1 :name "Meeting Notes" ...}))
  ;;   ;; => "ID: 1\nName: Meeting Notes\n..."
  (->cli [this]
    (str "ID: " (:id this)
         "\nName: " (:name this)
         "\nCreated At: " (:created-at this)
         "\nUpdated At: " (:updated-at this)
         "\nEntity ID: " (:entity-id this)
         "\nContent: " (:content this)))

  ;; Converts this Document into an LLM-compatible context object.
  ;;
  ;; Why use it:
  ;;   Used internally by the MCP resource layer so that when an LLM
  ;;   requests a document, the response is in a format the LLM can
  ;;   consume (an `:text-content` MCP content item).
  ;;
  ;; Arguments: None (called on the Document instance itself, `this`).
  ;;
  ;; Returns: A text-content map returned by `new-text-content`, which
  ;;          wraps the CLI string representation.
  ;;
  ;; Example:
  ;;   (->llm-context my-doc)
  ;;   ;; => {:type :text, :text "ID: 1\nName: Meeting Notes\n..."}
  (->llm-context [this]
    (new-text-content (->cli this))))

;; Converts a raw database result row into a Document record.
;;
;; Why use it:
;;   Used internally by query functions (e.g., `query-documents`) as the
;;   `:unmarshaller` callback. When next.jdbc returns rows from the database,
;;   they are plain maps with namespace-qualified keys (e.g., `:documents/id`).
;;   This function transforms each row into a proper `Document` record so
;;   the rest of the application can work with typed, well-structured data.
;;
;; Arguments:
;;   x - A map representing a single row from the `documents` table, with
;;       namespace-qualified keys: `:documents/id`, `:documents/name`,
;;       `:documents/description`, `:documents/content`,
;;       `:documents/created-at`, `:documents/updated-at`,
;;       `:documents/entity-id`.
;;
;; Returns: A `Document` record populated from the row.
;;
;; Example:
;;   (unmarshal-document {:documents/id 1
;;                        :documents/name "Meeting Notes"
;;                        :documents/description "Notes from the meeting"
;;                        :documents/content "We discussed Q2 goals..."
;;                        :documents/created-at #inst "2026-01-01"
;;                        :documents/updated-at #inst "2026-01-02"
;;                        :documents/entity-id 42})
;;   ;; => #legba.sql.document.Document{:id 1, :name "Meeting Notes", ...}
(defn- unmarshal-document [x]
  (Document. (:documents/id x)
             (:documents/name x)
             (:documents/content x)
             (:documents/created-at x)
             (:documents/updated-at x)
             (:documents/entity-id x)))

;; Creates a new Document and associates it with the given Entity.
;;
;; Why use it:
;;   Call this when you want to persist a new text note, log entry, or any
;;   other document-like content linked to a specific Entity in the
;;   knowledge graph. For example, an LLM might call this to save a
;;   summary of new information about a Person entity.
;;
;; Arguments:
;;   name        - A short, human-readable name for the document (String).
;;   description - A longer summary of what the document contains (String).
;;   content     - The full body/text of the document (String).
;;   entity-id   - The numeric ID of the Entity this document is attached
;;                 to (Integer / Long).
;;
;; Returns: A `Document` record representing the newly inserted row,
;;          with `:id` populated from the database and `:created-at` /
;;          `:updated-at` set to the current date-time.
;;
;; Example:
;;   (create-document
;;     "Meeting Notes"
;;     "Notes from the Q2 planning meeting"
;;     "We discussed the new feature roadmap and decided to..."
;;     42)
;;   ;; => #legba.sql.document.Document{:id 7, :name "Meeting Notes",
;;   ;;      :entity-id 42, :created-at #inst "2026-05-01", ...}
(defn create-document [name content entity-id]
  (first
   (do-query {:insert-into :documents
              :values [{:name name
                        :content content
                        :entity_id entity-id}]
              :returning :id}
             :unmarshaller (fn [x]
                             (Document. (:documents/id x)
                                        name
                                        content
                                        (java.util.Date.)
                                        (java.util.Date.)
                                        entity-id))
             :opts {:params {:created-at (java.util.Date.)}})))

(defn get-document-by-name [name]
  (first (do-query {:select :*
                    :from :documents
                    :where {:name name}
                    :unmarshaller unmarshal-document})))

(defn update-document [id name content]
  (first (do-query {:update :documents
                    :set {:name name
                          :content content}
                    :where {:id id}})))
;; Retrieves all Documents from the database.
;;
;; Why use it:
;;   Call this when you need a full listing of every document stored in
;;   the system. This is primarily used by the MCP resource layer to
;;   expose the complete documents index to the LLM, so the LLM can see
;;   what documents exist and then drill into specific ones.
;;
;;   NOTE: This function has no filtering or pagination. If the documents
;;   table grows large, consider adding limit/offset or query parameters.
;;
;; Arguments: None.
;;
;; Returns: A (possibly empty) sequence of `Document` records,
;;          one for each row in the `documents` table.
;;
;; Example:
;;   (query-documents)
;;   ;; => (#legba.sql.document.Document{:id 1, :name "Meeting Notes", ...}
;;   ;;     #legba.sql.document.Document{:id 2, :name "Research Log", ...})
(defn query-documents []
  (do-query {:select [:*]
             :from :documents}
            :unmarshaller unmarshal-document))
