#!/usr/bin/env bash
# Instalează binarul nativ ToonMint produs de CI (GraalVM native-image).
# Rulează pe server ca root: manual, sau automat prin runtime/deploy.sh (timer).
#
#   ./deploy-native.sh --file /tmp/toonmint-backend      # binar local
#   ./deploy-native.sh --latest                          # ultimul release GitHub
#   ./deploy-native.sh --tag v1.2.3                      # un release anume
#   ./deploy-native.sh --rollback                        # revine la binarul anterior
#   ./deploy-native.sh --latest --force                  # reinstalează chiar dacă e identic
#
# Garanții:
#  - validează că fișierul e ELF și are mărime plauzibilă înainte de instalare
#  - păstrează binarul anterior ca .prev (rollback instant)
#  - health check după restart; la eșec revine automat la versiunea anterioară
set -euo pipefail

APP_DIR="${TOONMINT_RUNTIME:-/root/ToonMint/runtime}"
BIN_DIR="$APP_DIR/bin"
CURRENT="$BIN_DIR/toonmint-backend"
PREVIOUS="$BIN_DIR/toonmint-backend.prev"
STAGING="$BIN_DIR/.staging-$$"
REPO="CsepregiArtur/ToonMint"
ASSET="toonmint-backend-linux-amd64"
SERVICE="toonmint-api"
HEALTH_URL="http://127.0.0.1:8080/actuator/health"
LOG="/var/log/toonmint-deploy.log"
MIN_BYTES=$((20 * 1024 * 1024))
NO_RESTART=0
FORCE=0
MODE="" VALUE=""

log() { echo "$(date -Is) $*" | tee -a "$LOG"; }

usage() { sed -n '2,14p' "$0"; exit 2; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --file|--tag) MODE="$1"; VALUE="${2:?}"; shift 2 ;;
        --latest)     MODE="--latest"; shift ;;
        --rollback)   MODE="--rollback"; shift ;;
        --no-restart) NO_RESTART=1; shift ;;
        --force)      FORCE=1; shift ;;
        -h|--help)    usage ;;
        *) echo "argument necunoscut: $1" >&2; usage ;;
    esac
done

[[ "$(id -u)" -eq 0 ]] || { echo "rulează ca root" >&2; exit 1; }
mkdir -p "$BIN_DIR"

verify_binary() {
    local f="$1"
    [[ -f "$f" ]] || { log "EROARE: $f nu există"; return 1; }
    local size; size=$(stat -c%s "$f")
    (( size >= MIN_BYTES )) || { log "EROARE: $f are doar $size B (aștept >20 MB)"; return 1; }
    # magic ELF: \x7fELF
    [[ "$(head -c4 "$f" | od -An -tx1 | tr -d ' \n')" == "7f454c46" ]] \
        || { log "EROARE: $f nu e binar ELF"; return 1; }
    chmod 755 "$f"
}

health_check() {
    local tries="${1:-10}"
    for ((i=1; i<=tries; i++)); do
        code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$HEALTH_URL" || echo 000)
        [[ "$code" == "200" ]] && { log "health OK ($code, încercarea $i)"; return 0; }
        sleep 2
    done
    log "EROARE: health check a eșuat după $tries încercări"
    return 1
}

restart_and_check() {
    log "systemctl restart $SERVICE"
    systemctl restart "$SERVICE"
    sleep 3
    systemctl is-active --quiet "$SERVICE" || { log "EROARE: serviciul nu e activ"; return 1; }
    health_check 10
}

rollback_to_previous() {
    [[ -x "$PREVIOUS" ]] || { log "EROARE: nu există binar anterior pentru rollback"; return 1; }
    log "ROLLBACK la binarul anterior"
    install -m 755 "$PREVIOUS" "$CURRENT"
    restart_and_check || log "EROARE: și rollback-ul a eșuat — intervenție manuală necesară"
}

download_release_asset() {
    local api_url="$1" meta url
    if ! meta=$(curl -fsSL --max-time 30 "$api_url" 2>/dev/null); then
        log "EROARE: niciun release disponibil (API: $api_url)"
        return 1
    fi
    url=$(printf '%s' "$meta" | python3 -c '
import json, sys
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)
name = sys.argv[1]
print(next((a.get("browser_download_url", "") for a in data.get("assets", [])
            if a.get("name") == name), ""))
' "$ASSET")
    if [[ -z "$url" ]]; then
        log "EROARE: release-ul nu conține asset-ul $ASSET"
        return 1
    fi
    log "descarc $url"
    if ! curl -fL --retry 3 --max-time 300 -o "$STAGING" "$url"; then
        log "EROARE: descărcarea a eșuat"
        return 1
    fi
}

case "$MODE" in
    --rollback)
        rollback_to_previous
        exit $?
        ;;
    --file)
        verify_binary "$VALUE" || exit 1
        cp "$VALUE" "$STAGING"
        ;;
    --tag)
        download_release_asset "https://api.github.com/repos/$REPO/releases/tags/$VALUE" >/dev/null || exit 1
        ;;
    --latest)
        download_release_asset "https://api.github.com/repos/$REPO/releases/latest" >/dev/null || exit 1
        ;;
    *)
        usage
        ;;
esac

verify_binary "$STAGING" || { rm -f "$STAGING"; exit 1; }
log "binar valid: $(stat -c%s "$STAGING") B"

# dacă e identic cu cel instalat, nu are rost să repornim aplicația
# (--force sare peste asta, pentru cazul în care s-au schimbat scripturile din runtime/)
if [[ $FORCE -eq 0 ]] && [[ -x "$CURRENT" ]] && cmp -s "$STAGING" "$CURRENT"; then
    log "binar identic cu cel instalat — nimic de făcut"
    rm -f "$STAGING"
    exit 0
fi

# păstrează versiunea curentă pentru rollback
if [[ -x "$CURRENT" ]]; then
    cp -a "$CURRENT" "$PREVIOUS"
    log "versiune anterioară salvată în $(basename "$PREVIOUS")"
fi

install -m 755 "$STAGING" "$CURRENT"
rm -f "$STAGING"
log "instalat: $CURRENT"

if [[ $NO_RESTART -eq 1 ]]; then
    log "instalat fără restart (--no-restart)"
    exit 0
fi

restart_and_check || { rollback_to_previous; exit 1; }
log "DEPLOY OK — rulează binarul nativ"
