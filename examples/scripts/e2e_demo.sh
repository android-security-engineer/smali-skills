#!/usr/bin/env bash
#
# End-to-end demo: assemble → disassemble → list → xref → search
#
# Walks the full smali-skills pipeline on the bundled HelloWorld example, showing every
# Layer-2 CLI capability. Run from the repo root after `./gradlew build`:
#
#   bash examples/scripts/e2e_demo.sh
#
# Exits non-zero if any step fails.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SMALI_JAR="$REPO_ROOT/smali/build/libs/smali.jar"
BAKSMALI_JAR="$REPO_ROOT/baksmali/build/libs/baksmali.jar"
EXAMPLE="$REPO_ROOT/examples/HelloWorld/HelloWorld.smali"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

DEX="$WORK_DIR/hello.dex"
SMALI_OUT="$WORK_DIR/disassembled"

echo "=== smali-skills end-to-end demo ==="
echo "work dir: $WORK_DIR"
echo

require_jar() {
    if [ ! -f "$1" ]; then
        echo "ERROR: $1 not found. Run './gradlew build' first." >&2
        exit 1
    fi
}
require_jar "$SMALI_JAR"
require_jar "$BAKSMALI_JAR"

echo ">>> 1. assemble: smali text -> dex"
java -jar "$SMALI_JAR" assemble "$EXAMPLE" -o "$DEX"
echo "    produced: $DEX ($(wc -c < "$DEX") bytes)"
echo

echo ">>> 2. disassemble: dex -> smali text"
java -jar "$BAKSMALI_JAR" disassemble "$DEX" -o "$SMALI_OUT"
echo "    produced: $(find "$SMALI_OUT" -name '*.smali' | wc -l) smali file(s) under $SMALI_OUT"
echo

echo ">>> 3. list classes (text + count)"
java -jar "$BAKSMALI_JAR" list classes "$DEX"
echo "    class count: $(java -jar "$BAKSMALI_JAR" list classes --count "$DEX")"
echo

echo ">>> 4. list methods --format json"
java -jar "$BAKSMALI_JAR" list methods --format json "$DEX"
echo

echo ">>> 5. list strings --group-by class (shows fields/methods grouping works)"
java -jar "$BAKSMALI_JAR" list methods --group-by class "$DEX"
echo

echo ">>> 6. xref callers: who calls println?"
java -jar "$BAKSMALI_JAR" xref callers --target "Ljava/io/PrintStream;->println(Ljava/lang/String;)V" "$DEX"
echo

echo ">>> 7. xref callers --format json: who calls System.out (field ref)?"
java -jar "$BAKSMALI_JAR" xref field-refs --target "Ljava/lang/System;->out:Ljava/io/PrintStream;" --format json "$DEX"
echo

echo ">>> 8. search --opcode const-string,invoke-virtual"
java -jar "$BAKSMALI_JAR" search --opcode "const-string,invoke-virtual" "$DEX"
echo

echo ">>> 9. search --opcode const-string,*,return-void (wildcard matches the invoke-virtual in between)"
java -jar "$BAKSMALI_JAR" search --opcode "const-string,*,return-void" "$DEX"
echo

echo "=== demo complete: assemble -> disassemble -> list -> xref -> search all succeeded ==="
