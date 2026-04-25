# legba

<picture>
  <source
    srcset="doc/legba-logo.png"
    media="(orientation: portrait)" />
  <img src="doc/legba-logo.png" alt="Legba logo" style="width:25%" />
</picture>
legba is my attempt at creating an MCP server that allows your LLM to learn as you use it.

## Overview

At a high level, it is a combination of a graph database that stores entities and relationships,
and a document store that allows exposing and interacting with the graph database through a
file-like API.
