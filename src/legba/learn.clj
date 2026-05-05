(ns legba.learn
  (:require [legba.llm.dag :as dag]))

; TODO add schema checking to process-dag
; find a document
; query for new relationship types?
; query for new entity types?

(def learn-dag {:nodes {:start {:callable (fn [state] nil)}}})
(defn learn []
  (dag/process-dag {:nodes {:start {:callable (fn [state] nil)}}} {}))
