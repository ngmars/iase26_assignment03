#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<USAGE
Usage (solo):
  ./prepare-submission.sh <Lastname> <Firstname>

Usage (group of two):
  ./prepare-submission.sh <Lastname1> <Firstname1> <Lastname2> <Firstname2>

Examples:
  ./prepare-submission.sh Mueller Anna
    -> ../iase26_assignment03_Mueller_Anna.zip

  ./prepare-submission.sh Mueller-Schmidt Anna Weber Berta
    -> ../iase26_assignment03_Mueller-Schmidt_Anna__Weber_Berta.zip
USAGE
}

if [ "$#" -ne 2 ] && [ "$#" -ne 4 ]; then
    usage
    exit 1
fi

if [ "$#" -eq 2 ]; then
    NAME_PART="$1_$2"
else
    NAME_PART="$1_$2__$3_$4"
fi

OUTPUT="../iase26_assignment03_${NAME_PART}.zip"

if [ ! -d ".git" ]; then
    echo "error: not a git repository (run from the root of your assignment clone)" >&2
    exit 1
fi

rm -f "$OUTPUT"

zip -r "$OUTPUT" . \
    -x "*/.gradle/*" -x ".gradle/*" \
    -x "*/.gradle"    -x ".gradle" \
    -x "*/build/*"    -x "build/*" \
    -x "*/build"      -x "build" \
    -x "*/.idea/*"    -x ".idea/*" \
    -x "*/.idea"      -x ".idea" \
    -x "*/.kotlin/*"  -x ".kotlin/*" \
    -x "*/.kotlin"    -x ".kotlin" \
    -x "*/bin/*"      -x "bin/*" \
    -x "*/out/*"      -x "out/*" \
    -x "*/.vscode/*"  -x ".vscode/*" \
    -x "*/.kt-coding-agent/*" -x ".kt-coding-agent/*" \
    -x "*.zip" \
    -x ".DS_Store" -x "*/.DS_Store"

echo "Wrote $OUTPUT"
