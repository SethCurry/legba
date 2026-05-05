(ns legba.llm.dag)

(defn process-dag [dag state]
  (loop [current-node (:start (:nodes dag))
         got-state state]
    (let [new-output ((:callable current-node) got-state)
          new-current-node (get-in new-output [:current-node])
          new-state (get-in new-output [:state])]
      (if (nil? new-current-node)
        new-state
        (recur (get (:nodes dag) new-current-node) new-state)))))
