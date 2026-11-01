#!/usr/bin/env bash
# Launcher pentru ToonMint: rulează binarul nativ (GraalVM) dacă există,
# altfel cade pe JVM. Astfel trecerea JVM -> native nu cere downtime sau
# schimbări în systemd: doar pui binarul în bin/ și repornești serviciul.
set -euo pipefail

APP_DIR="/root/ToonMint/runtime"
NATIVE="$APP_DIR/bin/toonmint-backend"
JAR="$APP_DIR/app.jar"
# Bind pe loopback: aplicația nu trebuie să asculte pe toate interfețele.
# Accesul public se face prin Cloudflare Tunnel. Se poate suprascrie cu
# TOONMINT_BIND=0.0.0.0 dacă apare vreodată nevoia.
BIND="${TOONMINT_BIND:-127.0.0.1}"

if [[ -x "$NATIVE" ]]; then
    echo "[run] runtime=native binary=$NATIVE bind=$BIND"
    exec "$NATIVE" "--server.address=$BIND"
fi

echo "[run] runtime=jvm jar=$JAR bind=$BIND"
# Opțiunile JVM vin din JAVA_TOOL_OPTIONS (EnvironmentFile/drop-in).
exec /usr/bin/java -jar "$JAR" "--server.address=$BIND"
