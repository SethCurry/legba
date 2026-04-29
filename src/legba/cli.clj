(ns legba.cli
  (:require [cli-matic.core :refer [run-cmd]]
            [legba.mcp :as mcp]
            [legba.tools :as tools]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.request :refer [path-info]]
            [taoensso.telemere :as t]
            [clojure.string :refer [lower-case]]
            [legba.sql.entity-type :as entity-type]
            [legba.sql.core :refer [->cli]]
            [legba.sql.relationship-type :as relationship-type]
            [legba.cli.flags :refer [flags]]
            [legba.sql.entity :as entity]))

(defn verbosity-to-log-level [verbosity]
  (case (lower-case verbosity)
    "error" :error
    "warn" :warn
    "info" :info
    "debug" :debug
    :default :info))

(defn- create-command-handler [callable]
  (fn [args]
  (let [str-log-level (get args :verbosity "info")
        log-level (verbosity-to-log-level str-log-level)
        new-args (dissoc args :verbosity)]
    (t/set-min-level! log-level)
    (callable new-args))))

(defn handler [mcp-handler]
  (fn [request]
    (try
      (let [url (path-info request)]
        (case url
          "/mcp" (let [response (mcp-handler request)]
                   (t/log! {:level :debug
                            :msg "Got MCP response"
                            :data {:response response}})
                   response)
          (do (t/log! {:level :debug
                       :msg "Not found"
                       :data {:url url}})
              {:status 404
               :headers {"Content-Type" "text/html"}
               :body "Not Found"})))
      (catch Exception e
        (t/log! {:level :error
                 :msg "Error in handler"
                 :data {:error e}})
        {:status 500
         :headers {"Content-Type" "text/html"}
         :body "Internal Server Error"}))))

(defn run-server [{:keys [port]
                   :or {port 3333}}]
  (let [mcp-handler (mcp/router [tools/create-entity-type-tool
                                 tools/create-entity-tool
                                 tools/query-entity-types-tool
                                 tools/query-relationship-types-tool
                                 tools/query-relationships-tool
                                 tools/create-relationship-type-tool
                                 tools/create-relationship-tool])
        root-handler (handler mcp-handler)]
  (run-jetty root-handler {:port port
                      :join? true})))

(defn print-models [models]
  (doall (map (fn [x] (println (->cli x))) models))
  "")

(defn list-entity-types [{}]
  (println "Entity Types:")
  (let [entity-types (entity-type/query-entity-types)]
    (print-models entity-types)))

(defn create-entity [{:keys [entity-type-name name]}]
  (println "Creating entity:")
  (println "Entity Type Name:" entity-type-name)
  (println "Name:" name)
  (let [found-entity-type (entity-type/get-entity-type-by-name entity-type-name)
        entity-type-id (:id found-entity-type)
        parsed-attributes {}
        new-entity (entity/create-entity entity-type-id name parsed-attributes)]
    (println (->cli new-entity))))

(defn list-entities [{}]
  (println "Entities:")
  (let [entities (entity/query-entities)]
    (print-models entities)))

(defn create-relationship-type [{:keys [name bidirectional description]}]
  (println "Creating relationship type:")
  (println "Name:" name)
  (println "Bidirectional:" bidirectional)
  (println "Description:" description)
  (let [new-relationship-type (relationship-type/create-relationship-type name bidirectional description)]
    (println (->cli new-relationship-type))))

(defn list-relationship-types [{}]
  (println "Relationship Types:")
  (let [relationship-types (relationship-type/query-relationship-types)]
    (print-models relationship-types)))

(defn create-entity-type [{:keys [name description]}]
  ; TODO: Ensure that name and description are not nil or empty strings
  (let [new-entity-type (entity-type/create-entity-type name description)]
    (println (->cli new-entity-type))
    ""))

(def cli-config {
                 :command "legba"
                 :description "A memory system for your AI"
                 :version "0.0.1"
                 :subcommands [{:command "server"
                                :description "Start the Legba server"
                                :examples ["java -jar legba.jar server --port 3017"]
                                :opts (flags [{:as "The port to listen on"
                                               :default 3333
                                               :option "port"
                                               :type :int}])
                                :runs (create-command-handler run-server)}
                               {:command "list"
                                :subcommands [{:command "entity-types"
                                               :description "List all entity types"
                                               :examples ["java -jar legba.jar list-entity-types"]
                                               :runs (create-command-handler list-entity-types)
                                               :opts (flags [])}
                                              {:command "entities"
                                               :description "List all entities"
                                               :examples ["java -jar legba.jar list-entities"]
                                               :runs (create-command-handler list-entities)
                                               :opts (flags [])}
                                              {:command "relationship-types"
                                               :description "List all relationship types"
                                               :examples ["java -jar legba.jar list-relationship-types"]
                                               :runs (create-command-handler list-relationship-types)
                                               :opts (flags [])}]}
                               {:command "create"
                                :subcommands [{:command "entity-type"
                                               :description "Create a new entity type"
                                               :examples ["java -jar legba.jar create-entity-type --name 'Person' --description 'A person'"]
                                               :runs (create-command-handler create-entity-type)
                                               :opts (flags [{:as "The name of the entity type to create"
                                                              :option "name"
                                                              :type :string
                                                              :required true}
                                                             {:as "The description of the entity type to create"
                                                              :option "description"
                                                              :type :string
                                                              :required true}])}
                                              {:command "entity"
                                               :description "Create a new entity"
                                               :examples ["java -jar legba.jar create-entity --entity-type-name 'Person' --name 'John Doe'"]
                                               :runs (create-command-handler create-entity)
                                               :opts (flags [{:as "The name of the entity type to create the entity for"
                                                              :option "entity-type-name"
                                                              :type :string
                                                              :required true}
                                                             {:as "The name of the entity to create"
                                                              :option "name"
                                                              :type :string
                                                              :required true}])}
                                              {:command "relationship-type"
                                               :description "Create a new relationship type"
                                               :examples ["java -jar legba.jar create-relationship-type --name 'Friend' --bidirectional true --description 'A friend relationship'"]
                                               :runs (create-command-handler create-relationship-type)
                                               :opts (flags [{:as "The name of the relationship type to create"
                                                              :option "name"
                                                              :type :string
                                                              :required true}
                                                             {:as "Whether the relationship is bidirectional"
                                                              :option "bidirectional"
                                                              :type :boolean
                                                              :default false}
                                                             {:as "The description of the relationship type to create"
                                                              :option "description"
                                                              :type :string
                                                              :required true}])}]}]})

(defn execute-cli [args]
  (run-cmd args cli-config))