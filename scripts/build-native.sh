#!/usr/bin/env bash
#
# Builds a standalone native binary of the ppjson CLI using GraalVM native-image.
#
# The resulting executable has no JVM dependency and starts in a few milliseconds.
# It is placed at target/native/ppjson.
#
# Usage:
#   ./scripts/build-native.sh           # build, exit on error
#   ./scripts/build-native.sh -v        # verbose native-image output
#   GRAALVM_JAVA=25.0.1-graal ./scripts/build-native.sh   # pin a specific sdkman candidate
#
# Requirements:
#   - sdkman (https://sdkman.io) with a GraalVM candidate installed
#   - Apache Maven on PATH (or via sdkman)
#
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# --- configuration -----------------------------------------------------------
GRAALVM_JAVA="${GRAALVM_JAVA:-25.0.1-graal}"
IMAGE_NAME="${IMAGE_NAME:-ppjson}"
OUTPUT_DIR="${OUTPUT_DIR:-target/native}"
VERBOSE="${VERBOSE:-0}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        -v|--verbose) VERBOSE=1; shift ;;
        -h|--help)
            sed -n '2,16p' "$0"
            exit 0
            ;;
        *) echo "Unknown option: $1" >&2; exit 2 ;;
    esac
done

# --- helpers -----------------------------------------------------------------
log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31mxx\033[0m %s\n' "$*" >&2; exit 1; }

# --- activate GraalVM via sdkman --------------------------------------------
if [[ -z "${SDKMAN_DIR:-}" ]]; then
    SDKMAN_DIR="$HOME/.sdkman"
fi
SDKMAN_INIT="$SDKMAN_DIR/bin/sdkman-init.sh"

if [[ ! -f "$SDKMAN_INIT" ]]; then
    die "sdkman not found at $SDKMAN_INIT — install it from https://sdkman.io"
fi

# shellcheck disable=SC1090
# sdkman scripts reference vars (ZSH_VERSION, SDKMAN_OFFLINE_MODE, ...) that
# may be unset under `set -u`; disable strict mode for the sourcing + `sdk use`.
set +u
source "$SDKMAN_INIT"

log "Using GraalVM: $GRAALVM_JAVA"
if ! sdk use java "$GRAALVM_JAVA" >/dev/null; then
    die "GraalVM candidate '$GRAALVM_JAVA' not installed. Run: sdk install java $GRAALVM_JAVA"
fi
set -u

command -v native-image >/dev/null 2>&1 \
    || die "native-image not on PATH. Run: gu install native-image  (older GraalVM) or install the full GraalVM distribution."

NATIVE_IMAGE_VERSION="$(native-image --version 2>&1 | head -1)"
log "native-image: $NATIVE_IMAGE_VERSION"

# --- build the jar -----------------------------------------------------------
log "Building jar (mvn -DskipTests package)"
cd "$PROJECT_ROOT"
mvn -q -DskipTests package

JAR="$(ls target/pym-json-*.jar 2>/dev/null | grep -v -E 'sources|javadoc' | head -1)"
[[ -n "$JAR" ]] || die "pym-json jar not found in target/"
log "Using jar: $JAR"

# --- run native-image --------------------------------------------------------
MAIN_CLASS="com.biroes.pym.json.cli.PrettyPrintCli"
mkdir -p "$OUTPUT_DIR"
readonly OUTPUT_PATH="$OUTPUT_DIR/$IMAGE_NAME"

log "Compiling native image → $OUTPUT_PATH"
ARGS=(
    -cp "$JAR"
    -H:Name="$OUTPUT_PATH"
    -H:Class="$MAIN_CLASS"
    --no-fallback
    -H:+ReportExceptionStackTraces
    --install-exit-handlers
)
if [[ "$VERBOSE" == "1" ]]; then
    native-image "${ARGS[@]}"
else
    native-image "${ARGS[@]}" 2>&1 | tail -20
fi

[[ -x "$OUTPUT_PATH" ]] || die "native-image did not produce $OUTPUT_PATH"

# --- report ------------------------------------------------------------------
SIZE="$(du -h "$OUTPUT_PATH" | cut -f1)"
log "Done: $OUTPUT_PATH ($SIZE)"
echo
echo "Smoke test:"
echo '{"hello":"world"}' | "$OUTPUT_PATH"
