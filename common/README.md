# Common Conventions

Shared rules every module follows, so modules stay consistent as the repo grows.

## Module Checklist
Every problem folder contains:

| File | Purpose |
|---|---|
| `README.md` | One-paragraph overview, embedded diagram, **local vs production** table, code map, run commands with sample output, intentional simplifications |
| `architecture/architecture.mmd` | Mermaid source of the production architecture |
| `architecture/architecture.svg` | Rendered diagram (never hand-edited) |
| `docs/design-summary.md` | Problem, requirements, high-level design, API, data model, decisions, bottlenecks, scaling |
| `docs/interview-questions.md` | 8–12 questions with short answers; capacity numbers state their assumptions |
| `pom.xml`, `src/` | Standalone Maven build, Java 21, JUnit 5 tests |

## Code Conventions
- **Readability first:** descriptive names, short methods, comments only where they explain a decision.
- **Plain Java by default.** Spring Boot only when an HTTP API is central to the problem.
- **Business rules stay framework-free** (e.g., `UrlShortenerService`), so tests construct them with `new`.
- **Time is injected** (`Clock` or `LongSupplier`), so tests never `sleep()`.
- **In-memory storage locally**, behind an interface only where production would swap it (e.g., a repository).
- **Honesty about guarantees:** if the local code is single-process, the README says so.

Package naming: `com.sdprep.<module>` (e.g., `com.sdprep.ratelimiter`).

## Diagrams

### Visual style
All diagrams use `flowchart LR` and the same node classes. Copy this block into new diagrams:

```text
classDef client fill:#E8F1FB,stroke:#2B6CB0,stroke-width:1.5px,color:#1A365D
classDef service fill:#E6F4EA,stroke:#2F855A,stroke-width:1.5px,color:#1C4532
classDef database fill:#FEF3E2,stroke:#C05621,stroke-width:1.5px,color:#652B19
classDef cache fill:#FDECEC,stroke:#C53030,stroke-width:1.5px,color:#63171B
classDef queue fill:#F3E8FD,stroke:#6B46C1,stroke-width:1.5px,color:#322659
classDef external fill:#EDF2F7,stroke:#4A5568,stroke-width:1.5px,stroke-dasharray:4 3,color:#1A202C
```

| Element | Shape | Class | Colour |
|---|---|---|---|
| Client / user / response | `([ ])` stadium | `client` | blue |
| Service / API / worker | `[ ]` rectangle | `service` | green |
| Database / durable store | `[( )]` cylinder | `database` | orange |
| Cache / in-memory store | `[( )]` cylinder or `[ ]` | `cache` | red |
| Queue / stream (Kafka) | `[[ ]]` subroutine | `queue` | purple |
| External system | `[ ]` rectangle | `external` | grey, dashed border |
| Decision | `{ }` diamond | `service` | green |

- **Solid arrow `-->`**: synchronous request path. **Dashed arrow `-.->`**: asynchronous flow.
- Label important edges; number steps (`1.`, `2.`) when order matters.
- Group related components with `subgraph`. Aim for ≤ 12 nodes and no crossing arrows.

### Rendering
Diagrams are rendered with the [Mermaid CLI](https://github.com/mermaid-js/mermaid-cli) (`@mermaid-js/mermaid-cli`, version pinned in [`diagrams/package.json`](diagrams/package.json)). It uses a headless Chrome that `npm install` downloads.

```bash
(cd common/diagrams && npm install)                # one-time setup
common/diagrams/render-diagrams.sh                  # render every module
common/diagrams/render-diagrams.sh 01-URL-Shortener # render one module
```

[`diagrams/mermaid-config.json`](diagrams/mermaid-config.json) sets the theme and disables HTML labels, so every label is real SVG `<text>` and displays correctly in GitHub, browsers, and IDE previews.
