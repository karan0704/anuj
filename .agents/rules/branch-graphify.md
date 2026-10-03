# Trigger: checking/updating the project's graphify output (distinct from rules/graphify.md's always-on query rules)

## Graphify Configuration & Verification Protocol
* **Single Code Graph**: this project has one code graph, at `D:\l Learning Projects\Master Android\graphify-out\`. There are no per-branch graph vaults.
* **Pre-Task Verification**: at the start of a task, check:
  1. Whether the project is a git repository yet, and if so the active branch (`git branch --show-current`).
  2. Whether `graphify-out\graph.json` exists. If it does not, say so and fall back to grep/read — do not invent graph results.
* **Explicit Notification Protocol**: ALWAYS notify the developer before updating Graphify by stating:
  - Active git branch name (or "not a git repository yet").
  - Exact Graphify output path being updated.
* **Post-Modification Auto-Update**: run `graphify update .` from the project root after code modifications to sync AST nodes with the graph.
