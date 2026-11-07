#!/usr/bin/env bash
# Launcher pentru ToonMint: rulează binarul nativ (GraalVM) dacă există,
# altfel cade pe JVM. Astfel trecerea JVM -> native nu cere downtime sau
# schimbări în systemd: doar pui binarul în bin/ și repornești serviciul.
set -euo pipefail

APP_DIR="${TOONMINT_RUNTIME:-/root/ToonMint/runtime}"
NATIVE="$APP_DIR/bin/toonmint-backend"
JAR="$APP_DIR/app.jar"
# Bind pe loopback: aplicația nu trebuie să asculte pe toate interfețele.
# Accesul public se face prin Cloudflare Tunnel. Se poate suprascrie cu
# TOONMINT_BIND=0.0.0.0 dacă apare vreodată nevoia.
BIND="${TOONMINT_BIND:-127.0.0.1}"

# AWT/Java2D: binarul nativ încarcă la runtime `libawt`/`libfontmanager`
# (`JNI_OnLoad`), iar acele biblioteci vin din JDK-ul instalat, nu din binar.
# Fără ele, fiecare job moare cu „UnsatisfiedLinkError: Can't load library: awt”
# și rămâne veșnic în PROCESSING. `libawt` depinde de `libjvm.so`, care stă în
# subdirectorul `server`, deci adăugăm ambele directoare.
java_lib_dir() {
    local candidate
    if [[ -n "${TOONMINT_JAVA_LIB:-}" ]]; then
        [[ -e "$TOONMINT_JAVA_LIB/libawt.so" ]] && { echo "$TOONMINT_JAVA_LIB"; return 0; }
        return 1
    fi
    for candidate in /usr/lib/jvm/*/lib; do
        [[ -e "$candidate/libawt.so" ]] && { echo "$candidate"; return 0; }
    done
    return 1
}

if lib_dir=$(java_lib_dir); then
    LD_LIBRARY_PATH="$lib_dir:$lib_dir/server${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
    export LD_LIBRARY_PATH
    echo "[run] java libs=$lib_dir"
fi

if [[ -x "$NATIVE" ]]; then
    echo "[run] runtime=native binary=$NATIVE bind=$BIND"
    exec "$NATIVE" "--server.address=$BIND"
fi

echo "[run] runtime=jvm jar=$JAR bind=$BIND"
# Opțiunile JVM vin din JAVA_TOOL_OPTIONS (EnvironmentFile/drop-in).
exec /usr/bin/java -jar "$JAR" "--server.address=$BIND"
