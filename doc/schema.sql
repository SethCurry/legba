CREATE TABLE entity_types (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL
);

CREATE TABLE entities (
    id SERIAL PRIMARY KEY,
    entity_type_id INTEGER NOT NULL REFERENCES entity_types(id),
    name TEXT NOT NULL,
    attributes JSONB NOT NULL
);

CREATE TABLE relationship_types (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    bidirectional BOOLEAN NOT NULL,
    description TEXT NOT NULL
);

CREATE TABLE relationships (
    id SERIAL PRIMARY KEY,
    relationship_type_id INTEGER NOT NULL REFERENCES relationship_types(id),
    source_entity_id INTEGER NOT NULL REFERENCES entities(id),
    target_entity_id INTEGER NOT NULL REFERENCES entities(id),
    attributes JSONB NOT NULL
);

CREATE TABLE documents (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    content TEXT NOT NULL,
    entity_id INTEGER NOT NULL REFERENCES entities(id)
);