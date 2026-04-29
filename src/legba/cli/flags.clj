(ns legba.cli.flags)

(def verbosity-flag {:as "The level to log at.  Must be one of: error, warn, info, debug"
                         :default "info"
                         :option "verbosity"
                         :type :string})

(defn flags [other-flags]
  (concat [verbosity-flag] other-flags))