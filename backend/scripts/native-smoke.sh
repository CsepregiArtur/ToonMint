#!/usr/bin/env bash
# Fluxul minim care exercită API-ul, coada de job-uri și pipeline-ul de imagine
# (ImageIO + Thumbnailator). Folosit identic pe JVM (colectarea de metadata cu
# tracing agent) și pe binarul nativ.
set -uo pipefail

BASE_URL="${1:-http://localhost:8080}"

fail() {
    echo "SMOKE FAILED: $*" >&2
    exit 1
}

health=""
for _ in $(seq 1 300); do
    health=$(curl -fsS "$BASE_URL/actuator/health" 2>/dev/null) && break
    sleep 0.2
done
[ -n "$health" ] || fail "health endpoint never reported UP"

# /actuator/health răspunde înainte ca ApplicationRunner-ele să termine, iar
# conturile demo sunt create de DemoDataSeeder, deci se reîncearcă.
token=""
for _ in $(seq 1 30); do
    token=$(curl -sS -X POST "$BASE_URL/api/auth/demo-login" \
        -H 'Content-Type: application/json' -d '{}' 2>/dev/null | jq -r '.token // empty')
    [ -n "$token" ] && break
    sleep 1
done
[ -n "$token" ] || fail "demo-login returned no token"

style=$(curl -sS "$BASE_URL/api/styles" 2>/dev/null | jq -r '.[0].id // empty')
[ -n "$style" ] || fail "style catalog is empty"

# 1400x1400 forțează downscale-ul din ImageOptimizer (max 1024).
png=$(mktemp)
python3 - "$png" <<'PY'
import struct, sys, zlib

w = h = 1400
raw = (b'\x00' + bytes([200, 120, 40]) * w) * h


def chunk(kind, data):
    return (struct.pack('>I', len(data)) + kind + data
            + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff))


png = (b'\x89PNG\r\n\x1a\n'
       + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
       + chunk(b'IDAT', zlib.compress(raw, 9))
       + chunk(b'IEND', b''))
open(sys.argv[1], 'wb').write(png)
PY

created=$(curl -sS -X POST "$BASE_URL/api/jobs" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"styleId\":\"$style\",\"imageBase64\":\"$(base64 < "$png" | tr -d '\n')\"}")
job=$(printf '%s' "$created" | jq -r '.id // empty' 2>/dev/null)
[ -n "$job" ] || fail "job creation failed: $created"
echo "job $job created"

status=""
for _ in $(seq 1 60); do
    status=$(curl -sS -H "Authorization: Bearer $token" "$BASE_URL/api/jobs/$job" 2>/dev/null \
        | jq -r '.status // empty' 2>/dev/null)
    [ "$status" = "COMPLETED" ] && break
    [ "$status" = "FAILED" ] && fail "job reported FAILED"
    sleep 1
done
[ "$status" = "COMPLETED" ] || fail "job ended in state '$status'"

curl -fsS -o /dev/null -H "Authorization: Bearer $token" \
    "$BASE_URL/api/jobs/$job/download" || fail "download failed"

# JPEG trece printr-un decodor nativ (libjavajpeg, prin JNI), spre deosebire de PNG,
# care e pur Java. Un binar fără callback-urile `JPEGImageReader` în jni-config pică
# exact aici, cu „NoSuchMethodError: JPEGImageReader.readInputData”, deși PNG-ul trece.
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
JPEG_FILE="$SCRIPT_DIR/../../frontend/public/demo/before.jpg"
[ -f "$JPEG_FILE" ] || fail "lipsește imaginea JPEG de test ($JPEG_FILE)"

payload=$(mktemp)
printf '{"styleId":"%s","imageBase64":"%s"}' "$style" "$(base64 < "$JPEG_FILE" | tr -d '\n')" > "$payload"

created=$(curl -sS -X POST "$BASE_URL/api/jobs" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    --data @"$payload")
job=$(printf '%s' "$created" | jq -r '.id // empty' 2>/dev/null)
[ -n "$job" ] || fail "JPEG job creation failed: $created"
echo "jpeg job $job created"

status=""
for _ in $(seq 1 60); do
    status=$(curl -sS -H "Authorization: Bearer $token" "$BASE_URL/api/jobs/$job" 2>/dev/null \
        | jq -r '.status // empty' 2>/dev/null)
    [ "$status" = "COMPLETED" ] && break
    [ "$status" = "FAILED" ] && fail "JPEG job reported FAILED (decodor JPEG lipsă?)"
    sleep 1
done
[ "$status" = "COMPLETED" ] || fail "JPEG job ended in state '$status'"

echo "SMOKE OK"
