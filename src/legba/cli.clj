(ns legba.cli
  (:require [cli-matic.core :refer [run-cmd]]))


(def cli-config {
                 :command "legba"
                 :description "A memory system for your AI"
                 :version "0.0.1"
                 :opts [{
                         :as "The level to log at"
                         :default "info"
                         :option "verbosity"
                         :type :str
                 }]
                 :subcommands [{
                                :command "server"
                                :description "Start the Legba server"
                                :examples [""]
                                :opts [{
                                        :as "The port to listen on"
                                        :default 3333
                                        :option "port"
                                }]
                                :runs (fn [{:keys [verbosity port]}] (legba.core/run-server (:port args)))
                 }]
})

(defn execute-cli [args]
  (run-cmd args cli-config))