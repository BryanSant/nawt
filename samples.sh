#!/usr/bin/env bash
# List or run NAWT sample apps. Works on macOS and Linux.
#
# Usage:
#   ./samples.sh                   # list samples
#   ./samples.sh <name>            # run a sample
#   ./samples.sh <name> -- <args>  # run a sample, forwarding extra Gradle args
#   NAWT_BACKEND=gtk ./samples.sh tier1   # pin the backend

set -euo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
GRADLEW="$HERE/gradlew"

# name | gradle task | one-line description
SAMPLES=(
    "hello       | :nawt-samples:run         | Smallest possible NAWT app — one window, one label."
    "demo        | :nawt-samples:demo        | Interactive demo — menu bar, list view, dialogs."
    "tier1      | :nawt-samples:tier1       | Exercises every Tier 1 widget."
    "calculator  | :nawt-samples:calculator  | Four-function calculator on a Grid."
    "smoke       | :nawt-samples:smoke       | Non-interactive smoke test — opens a window and quits."
    "tier1smoke  | :nawt-samples:tier1Smoke  | Non-interactive Tier 1 smoke — constructs every widget and quits."
)

list_samples() {
    printf "Available NAWT samples:\n\n"
    printf "  %-12s  %s\n" "NAME" "DESCRIPTION"
    printf "  %-12s  %s\n" "----" "-----------"
    for row in "${SAMPLES[@]}"; do
        IFS='|' read -r name _task desc <<<"$row"
        name="${name// /}"
        desc="${desc# }"
        printf "  %-12s  %s\n" "$name" "$desc"
    done
    printf "\nRun a sample:  ./samples.sh <name>\n"
    printf "Pin backend:   NAWT_BACKEND=macos|gtk ./samples.sh <name>\n"
}

lookup_task() {
    local query="$1"
    for row in "${SAMPLES[@]}"; do
        IFS='|' read -r name task _desc <<<"$row"
        name="${name// /}"
        task="${task// /}"
        if [[ "$name" == "$query" ]]; then
            printf '%s' "$task"
            return 0
        fi
    done
    return 1
}

case "${1:-}" in
    ""|list|ls|-l|--list)
        list_samples
        exit 0
        ;;
    -h|help|--help)
        list_samples
        exit 0
        ;;
esac

name="$1"
shift

if ! task="$(lookup_task "$name")"; then
    printf "samples.sh: unknown sample '%s'\n\n" "$name" >&2
    list_samples >&2
    exit 1
fi

GRADLE_ARGS=()
if [[ -n "${NAWT_BACKEND:-}" ]]; then
    GRADLE_ARGS+=("-Dnawt.backend=${NAWT_BACKEND}")
fi

# Anything after `--` is forwarded as extra Gradle args.
if [[ "${1:-}" == "--" ]]; then
    shift
    GRADLE_ARGS+=("$@")
fi

exec "$GRADLEW" "$task" "${GRADLE_ARGS[@]}"
