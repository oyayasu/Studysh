#!/usr/bin/env bash
# Build/package eSpeak data for every Piper language offered by Studysh.
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PIPER_ROOT="$PROJECT_ROOT/piper-kotlin"
ESPEAK_ROOT="$PIPER_ROOT/third-party/espeak-ng"
BUILD="$ESPEAK_ROOT/build"

test "$(sed -n 's/^piper.ortVersion=//p' "$PIPER_ROOT/gradle.properties")" = "1.31.0"
bash "$PIPER_ROOT/scripts/fetch-native-deps.sh"
bash "$PIPER_ROOT/scripts/stage-espeak-data.sh"

# Upstream only compiles en_dict for its English device tests. Studysh also
# offers Portuguese and Spanish, so compile their actual pronunciation rules.
export ESPEAK_DATA_PATH="$BUILD"
for language in pt es; do
    dict_tmp="$(mktemp -d "$BUILD/studysh-dictionary-XXXXXX")"
    cp "$ESPEAK_ROOT/dictsource/${language}_rules" \
       "$ESPEAK_ROOT/dictsource/${language}_list" \
       "$ESPEAK_ROOT/dictsource/${language}_emoji" "$dict_tmp/"
    (cd "$dict_tmp" && "$BUILD/src/espeak-ng" --compile="$language")
    rm -rf "$dict_tmp"
done
for language in en pt es; do
    test -s "$BUILD/espeak-ng-data/${language}_dict"
done
test -n "$("$BUILD/src/espeak-ng" -v en-us -q --ipa 'The artist draws a cat.')"
test -n "$("$BUILD/src/espeak-ng" -v pt-br -q --ipa 'Olá, vamos estudar português.')"
test -n "$("$BUILD/src/espeak-ng" -v es -q --ipa 'Hola, vamos a estudiar español.')"
mkdir -p "$PROJECT_ROOT/app/src/main/assets/espeak-ng-data"
cp -a "$BUILD/espeak-ng-data/." "$PROJECT_ROOT/app/src/main/assets/espeak-ng-data/"
echo "Piper data packaged and host phonemization checked for English, Portuguese and Spanish."
