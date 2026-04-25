(ns legba.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.request :refer [path-info]]
            [taoensso.telemere :as t]
            [legba.mcp :as mcp]
            [legba.tools :as tools])
  (:gen-class))

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

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (t/set-min-level! :debug)
  (let [mcp-handler (mcp/router [tools/create-entity-type-tool
                                 tools/create-entity-tool
                                 tools/query-entity-types-tool
                                 tools/query-relationship-types-tool
                                 tools/query-relationships-tool
                                 tools/create-relationship-type-tool
                                 tools/create-relationship-tool])
        root-handler (handler mcp-handler)]
  (run-jetty root-handler {:port 3333
                      :join? true})))
