(ns legba.core
  (:require
   [clojure.tools.cli :refer [parse-opts]]
   [legba.mcp :as mcp]
   [legba.sql.entity :as entity]
   [legba.tools :as tools]
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.util.request :refer [path-info]]
   [taoensso.telemere :as t])
  (:gen-class))

(def cli-options
  ;; An option with an argument
  [["-p" "--port PORT" "Port number"
    :default 3333
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 65536) "Must be a number between 0 and 65536"]]
   ;; A non-idempotent option (:default is applied first)
   ["-v" nil "Verbosity level"
    :id :verbosity
    :default 0
    :update-fn inc] ; Prior to 0.4.1, you would have to use:
   ;; :assoc-fn (fn [m k _] (update-in m [k] inc))
   ;; A boolean option defaulting to nil
   ["-h" "--help"]])

(defn verbosity-to-log-level [verbosity]
  (case verbosity
    0 :error
    1 :warn
    2 :info
    3 :debug
    :default :debug))

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

(defn run-server [port]
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

(defn -main
  [& args]
  (let [{:keys [options arguments summary]} (parse-opts args cli-options)
        command-name (first arguments)]
    (t/set-min-level! (verbosity-to-log-level (:verbosity options)))
    (case command-name
      "server" (run-server (:port options))
      "list" (case (second arguments)
               "entities" (entity/query-entities))
      (do (t/log! {:level :error
                   :msg "Unknown command"
                   :data {:command (first arguments)}})
          (println summary)
          (System/exit 1)))))