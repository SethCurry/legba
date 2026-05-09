(ns legba.cli.flags
  "Command-line flag definitions for the legba CLI application.
   Provides a common verbosity flag and a helper to compose flags
   into a single collection for use with CLI argument parsers.")

(def verbosity-flag
  {:as      "The level to log at.  Must be one of: error, warn, info, debug"
   :default "info"
   :option  "verbosity"
   :type    :string})

;; ---------------------------------------------------------------------------
;; flags
;; ---------------------------------------------------------------------------
;;
;; Purpose:
;;   Builds a complete flags vector by prepending the shared `verbosity-flag`
;;   to the caller-supplied flags.  This ensures every subcommand inherits
;;   the verbosity option without duplicating its definition.
;;
;;   Use this when assembling the flag spec for a CLI subcommand so that
;;   `--verbosity` is always available.
;;
;; Arguments:
;;   other-flags  --  A sequential collection (vector/list) of flag maps
;;                    specific to the subcommand.
;;
;; Returns:
;;   A lazy sequence whose first element is `verbosity-flag` followed by
;;   every element in `other-flags`.
;;
;; Example:
;;   ;; Composing flags for a "serve" subcommand:
;;   (flags [{:option "port" :type :int :default 8080
;;            :as "Port to listen on"}])
;;   ;; => ({:as "The level to log at. ..."
;;   ;;      :default "info"
;;   ;;      :option "verbosity"
;;   ;;      :type :string}
;;   ;;     {:option "port"
;;   ;;      :type :int
;;   ;;      :default 8080
;;   ;;      :as "Port to listen on"})
;;
(defn flags
  "Returns a flag collection that always includes the shared verbosity flag."
  [other-flags]
  (concat [verbosity-flag] other-flags))
