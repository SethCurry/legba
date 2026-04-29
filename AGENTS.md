# LLM Instructions for legba

## Project Purpose
legba is an MCP (Model Context Protocol) server that allows LLMs to maintain a persistent knowledge graph of entities and relationships, along with associated documents. It enables "learning" by providing a way for the LLM to store and retrieve structured knowledge across sessions.

## Tech Stack
- **Language:** Clojure 1.12.2
- **Database:** PostgreSQL with `pgvector`
- **SQL Generation:** HoneySQL
- **DB Connectivity:** next.jdbc and HikariCP
- **Communication:** JSON-RPC / MCP
- **Validation:** Prismatic Schema

## Core Domain Model
The project implements a graph-like structure in a relational database:
- **Entity Types:** Definitions of what can be an entity (e.g., "Person", "Project").
- **Entities:** Individual instances of an Entity Type, containing a name and a JSONB map of attributes.
- **Relationship Types:** Definitions of how entities can relate (e.g., "Works On", "Is Part Of"), including whether the relationship is bidirectional.
- **Relationships:** Directed links between two entities, associated with a Relationship Type and a JSONB map of attributes.
- **Documents:** Text-based notes or documents linked to a specific Entity.

## Architecture & Code Structure
- `src/legba/sql/`: Contains the data access layer.
    - `core.clj`: General SQL utilities.
    - `entity_type.clj`, `entity.clj`, `relationship_type.clj`, `relationship.clj`, `document.clj`: Domain-specific CRUD and query logic.
- `src/legba/mcp.clj`: Implements the MCP server logic, defining tools and resources exposed to the LLM.
- `src/legba/jsonrpc.clj`: Handles the JSON-RPC communication layer.
- `src/legba/cli/`: Handles command-line arguments and configuration.
- `src/legba/core.clj`: Main entry point; initializes the application and starts the server.
- `doc/schema.sql`: The source of truth for the database schema.

## Development Guidelines

### Clojure Idioms
- Prioritize functional purity and immutable data structures.
- Use `let` blocks for intermediate calculations.
- Prefer `map`, `filter`, and `reduce` over explicit loops.

### Database Interactions
- **Always** use HoneySQL for constructing SQL queries. Avoid raw SQL strings for anything other than schema migrations.
- Ensure that any changes to the database schema are documented in `doc/schema.sql`.
- When adding new fields to entities or relationships, use the `attributes` JSONB column unless the field is required for indexing or core relational integrity.

### MCP Implementation
- When adding new tools to the MCP server, ensure they are clearly named and have descriptive parameters so the LLM knows how to use them.
- Use `prismatic/schema` for validating input parameters to MCP tools.

### Testing
- Add unit tests for new logic in the `test/legba/` directory.
- Focus on testing the SQL generation and the MCP tool handlers.

## Common Workflows
- **Adding a new Tool:** 
    1. Define the logic in `src/legba/sql/` or `src/legba/core.clj`.
    2. Register the tool in `src/legba/mcp.clj`.
    3. Add a corresponding test case.
- **Modifying the Schema:**
    1. Edit `doc/schema.sql`.
    2. Update the relevant files in `src/legba/sql/`.
    3. Update tests to reflect the new schema.
