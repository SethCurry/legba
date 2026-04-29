(ns legba.core
  (:require
   [legba.cli :refer [execute-cli]])
  (:gen-class))


(defn -main
  [& args]
  (execute-cli args))