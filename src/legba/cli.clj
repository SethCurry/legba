(ns legba.cli
  (:require [cli-matic.core :refer [run-cmd]]
            [legba.mcp :as mcp]
            [legba.sql.entity :as entity]
            [legba.tools :as tools]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.request :refer [path-info]]
            [taoensso.telemere :as t]
            [clojure.string :refer [lower-case]]
            [legba.sql.entity-type :as entity-type]
            [legba.sql.core :refer [->cli]]))

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

(defn list-entity-types [{}]
  (let [entity-types (entity-type/query-entity-types)]
    (doall (map (fn [x] (println (->cli x))) entity-types))
    ""))

(defn create-entity-type [{:keys [name description]}]
  ; TODO: Ensure that name and description are not nil or empty strings
  (let [new-entity-type (entity-type/create-entity-type name description)]
    (println (->cli new-entity-type))
    ""))

(def cli-config {
                 :command "legba"
                 :description "A memory system for your AI"
                 :version "0.0.1"
                 :opts [{:as "The level to log at"
                         :default "info"
                         :option "verbosity"
                         :type :string}]
                 :subcommands [{:command "server"
                                :description "Start the Legba server"
                                :examples ["java -jar legba.jar server --port 3017"]
                                :opts [{:as "The port to listen on"
                                        :default 3333
                                        :option "port"
                                        :type :int}]
                                :runs (create-command-handler run-server)}
                               {:command "list-entity-types"
                                :description "List all entity types"
                                :examples ["java -jar legba.jar list-entity-types"]
                                :runs (create-command-handler list-entity-types)}
                               {:command "create-entity-type"
                                :description "Create a new entity type"
                                :examples ["java -jar legba.jar create-entity-type --name 'Person' --description 'A person'"]
                                :runs (create-command-handler create-entity-type)
                                :opts [{:as "The name of the entity type to create"
                                        :option "name"
                                        :type :string
                                        :required true}
                                       {:as "The description of the entity type to create"
                                        :option "description"
                                        :type :string
                                        :required true}]}]
})

(defn execute-cli [args]
  (run-cmd args cli-config))