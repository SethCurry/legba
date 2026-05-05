(ns legba.llm.dag)

(defn process-dag
  "Traverses a directed acyclic graph of callable nodes, threading mutable state
  through each node along the way.

  This function implements a simple and extensible DAG execution engine. Each
  node in the DAG is a map containing a `:callable` function. Starting from the
  node identified by the `:start` key within the DAG's `:nodes` map, `process-dag`
  invokes each node's callable with the current state, receives back a map
  describing a (possibly updated) state and the identifier of the next node to
  visit, and continues following the chain until a node signals completion by
  returning a nil `:current-node`.

  ## Why use it?

  Use `process-dag` whenever you need to model a multi-step workflow where:

  - Steps may **conditionally route** to different successors at runtime based
    on the accumulated state.
  - A piece of shared, evolving **state must be threaded through** every step
    and transformed incrementally.
  - The control flow is easier to express as an **explicit graph** (nodes and
    edges) rather than as deeply nested conditionals or a rigid linear pipeline.
  - You want to **decouple the definition of each step** from the overall
    execution order, making the workflow easy to inspect, serialize, extend, or
    even generate at runtime.

  ## Arguments

  ### `dag` — the graph to traverse

  A map with exactly two keys:

  - `:start` — the identifier (key) of the first node to execute. This key must
    exist inside the `:nodes` map.
  - `:nodes` — a map where each **key** is a unique node identifier and each
    **value** is a node map with the following shape:

    ```clojure
    {:callable (fn [state] -> {:current-node next-id-or-nil
                               :state       updated-state})}
    ```

    | Field | Type | Required | Description |
    |---|---|---|---|
    | `:callable` | `(fn [s] -> map?)` | **yes** | A function that receives the current state and returns a map. |

    The map returned by `:callable` must contain:

    | Field | Type | Required | Description |
    |---|---|---|---|
    | `:current-node` | `keyword?`, `nil` | **yes** | The key of the next node to visit, or `nil` to stop traversal. |
    | `:state` | `any` | **yes** | The (potentially modified) state to pass to the next node. |

  ### `state` — the initial value threaded through the DAG

  The starting state that will be passed to the first node's `:callable`.  It
  can be **any value** — a simple map, a Clojure atom, an integer accumulator,
  a record, etc.  Each node is responsible for returning the (updated) state
  that the next node should receive.

  ## Returns

  The **final state value** returned by the terminating node (the node whose
  `:callable` returned `:current-node nil`).  This is the fully transformed
  state after traversing the chain of nodes prescribed by the DAG.

  ## Basic example

  ```clojure
  ;; Define a simple two-step DAG that greets a user and then asks for their
  ;; age.  The second node stops the traversal by returning nil :current-node.
  (def example-dag
    {:start :greet
     :nodes {:greet   {:callable (fn [s]
                                   {:current-node :ask-age
                                    :state (assoc s :greeting \"Hello!\")})}
             :ask-age {:callable (fn [s]
                                   {:current-node nil
                                    :state (assoc s :age 30)})}}})

  ;; Run the DAG with an initial state map.
  (process-dag example-dag {:name \"Alice\"})
  ;; => {:name \"Alice\", :greeting \"Hello!\", :age 30}
  ```

  ## Conditional routing example

  Because each `:callable` inspects the current state before choosing a
  successor, you can implement branching logic trivially:

  ```clojure
  (def branching-dag
    {:start :check-admin
     :nodes {:check-admin {:callable (fn [s]
                                       (if (:admin? s)
                                         {:current-node :admin-greet
                                          :state s}
                                         {:current-node :guest-greet
                                          :state s}))}
             :admin-greet {:callable (fn [s]
                                       {:current-node nil
                                        :state (assoc s :message \"Welcome, admin!\")})}
             :guest-greet {:callable (fn [s]
                                       {:current-node nil
                                        :state (assoc s :message \"Welcome, guest!\")})}}})

  (process-dag branching-dag {:admin? true, :username \"root\"})
  ;; => {:admin? true, :username \"root\", :message \"Welcome, admin!\"}

  (process-dag branching-dag {:admin? false, :username \"visitor\"})
  ;; => {:admin? false, :username \"visitor\", :message \"Welcome, guest!\"}
  ```"
  [dag state]
  (loop [current-node (:start (:nodes dag))
         got-state state]
    (let [new-output ((:callable current-node) got-state)
          new-current-node (get-in new-output [:current-node])
          new-state (get-in new-output [:state])]
      (if (nil? new-current-node)
        new-state
        (recur (get (:nodes dag) new-current-node) new-state)))))
