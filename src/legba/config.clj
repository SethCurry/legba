(ns legba.config
  (:require [clojure.java.io :as io]
            [legba.json :refer [<-json]]
            [schema.core :as s]
            [taoensso.telemere :as t]))

(s/defschema Config
  "A configuration for the Legba server."
  {:database {:hostname s/Str
              :port s/Int
              :username s/Str
              :password s/Str
              :database s/Str}})

(defn config-file
  "Resolves ~/.config/legba/config.json using the process user.home."
  []
  (io/file (System/getProperty "user.home") ".config" "legba" "config.json"))

(defn read-config
  "Reads JSON from `path` (File, string path, or URI) into a Clojure map with
  keyword keys. With no args, reads the default ~/.config/legba/config.json"
  ([] (read-config (config-file)))
  ([path]
   (let [parsed-config (<-json (io/file path) :schema Config)]
     (t/log! {:level :debug :msg "Parsed config" :data {:parsed-config parsed-config}})
     parsed-config)))


(defonce loaded-config
  (delay (read-config)))