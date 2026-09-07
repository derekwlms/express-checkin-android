#!/usr/bin/env bash
# Open the Breeze request files in the resterm TUI.
#
#   ./open.sh                 # start on checkin.http
#   ./open.sh people.http     # start on another file
#   ./open.sh events.http -e mock
#
# Inside the TUI: Ctrl+Enter runs the request under the cursor, Ctrl+E
# switches environment, / filters the navigator, ? shows all keys.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

if ! command -v resterm >/dev/null 2>&1; then
  echo "resterm not found. Install it with: brew install resterm" >&2
  exit 1
fi

file="${1:-checkin.http}"
if [[ $# -gt 0 ]]; then
  shift
fi

if [[ ! -f $file ]]; then
  echo "No such request file: $file" >&2
  echo "Available: $(ls -- *.http | tr '\n' ' ')" >&2
  exit 1
fi

exec resterm --workspace . --recursive --env sgc --file "$file" "$@"
