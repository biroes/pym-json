#!/usr/bin/env bash
#
# Smoke-tests the native ppjson binary against scripts/test.json.
#
# Usage:
#   ./scripts/test-native.sh                # default modes
#   ./scripts/test-native.sh -v             # also show raw diff vs input
#
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly BINARY="$PROJECT_ROOT/target/native/ppjson"
readonly FIXTURE="$SCRIPT_DIR/test.json"

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mxx\033[0m %s\n' "$*" >&2; exit 1; }

[[ -x "$BINARY" ]] || fail "Binary not found: $BINARY (run ./scripts/build-native.sh first)"
[[ -f "$FIXTURE" ]] || fail "Fixture not found: $FIXTURE"

log "Binary:   $BINARY"
log "Fixture:  $FIXTURE"
echo

run() {
    local label="$1"; shift
    log "$label"
    "$@"
    echo
}

run "pretty (default)"      "$BINARY" "$FIXTURE"
run "compact"               "$BINARY" --compact "$FIXTURE"
run "indent=4"              "$BINARY" --indent=4 "$FIXTURE"
run "tab"                   "$BINARY" --tab "$FIXTURE"
run "no-nulls"              "$BINARY" --no-nulls "$FIXTURE"
run "via stdin"             bash -c "cat '$FIXTURE' | '$BINARY'"
run "via stdin (-)"         bash -c "cat '$FIXTURE' | '$BINARY' -"

log "All modes ran successfully."
