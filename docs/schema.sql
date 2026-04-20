CREATE TABLE node_types (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL
)

CREATE TABLE nodes (
    id SERIAL PRIMARY KEY,
    node_type_id INT NOT NULL REFERENCES node_types(id),
    attributes JSONB NOT NULL
);

CREATE TABLE edge_types (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    bidirectional BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT NOT NULL
);

CREATE TABLE edges (
    id SERIAL PRIMARY KEY,
    edge_type_id INT NOT NULL REFERENCES edge_types(id),
    source_node_id INT NOT NULL REFERENCES nodes(id),
    target_node_id INT NOT NULL REFERENCES nodes(id),
    attributes JSONB NOT NULL
);

CREATE TABLE documents (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    node_id INT REFERENCES nodes(id)
);

CREATE TABLE sessions (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE session_messages (
    id SERIAL PRIMARY KEY,
    session_id INT NOT NULL REFERENCES sessions(id),
    role VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);