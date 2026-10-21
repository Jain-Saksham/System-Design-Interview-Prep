#!/usr/bin/env bash
# Renders every <module>/architecture/architecture.mmd into architecture.svg.
# Usage (from anywhere):  common/diagrams/render-diagrams.sh [module-folder ...]
# With no arguments it renders all modules.
set -euo pipefail

DIAGRAMS_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$DIAGRAMS_DIR/../.." && pwd)"
MMDC="$DIAGRAMS_DIR/node_modules/.bin/mmdc"

if [[ ! -x "$MMDC" ]]; then
  echo "Mermaid CLI not installed. Run: (cd common/diagrams && npm install)" >&2
  exit 1
fi

if [[ $# -gt 0 ]]; then
  modules=("$@")
else
  modules=()
  for dir in "$REPO_ROOT"/[0-9][0-9]-*/; do modules+=("$(basename "$dir")"); done
fi

for module in "${modules[@]}"; do
  source_file="$REPO_ROOT/$module/architecture/architecture.mmd"
  [[ -f "$source_file" ]] || { echo "skip  $module (no architecture.mmd)"; continue; }
  "$MMDC" --quiet \
    --input "$source_file" \
    --output "${source_file%.mmd}.svg" \
    --configFile "$DIAGRAMS_DIR/mermaid-config.json" \
    --backgroundColor white
  echo "ok    $module/architecture/architecture.svg"
done
