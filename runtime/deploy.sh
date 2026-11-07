#!/usr/bin/env bash
# Punctul de intrare pentru deploy-ul automat (toonmint-deploy.timer):
# actualizează codul, publică build-ul de frontend și instalează ultimul binar nativ.
#
#   ./deploy.sh                    # git pull + frontend (dacă s-a schimbat) + binar
#   ./deploy.sh --tag native-x     # git pull + un release anume
#   ./deploy.sh --rollback         # git pull + revine la binarul anterior
#   ./deploy.sh --skip-frontend    # nu reconstruiește frontend-ul
#
# Pull-ul e cel care aduce run.sh și deploy-native.sh la zi; fără el, timer-ul ar
# rula mereu versiunea veche a scripturilor.
set -uo pipefail

REPO="${TOONMINT_REPO:-/root/ToonMint}"
cd "$REPO" || exit 1

SKIP_FRONTEND=0
arguments=()
for arg in "$@"; do
    case "$arg" in
        --skip-frontend) SKIP_FRONTEND=1 ;;
        *) arguments+=("$arg") ;;
    esac
done
[[ ${#arguments[@]} -eq 0 ]] && arguments=(--latest)

before=$(git rev-parse HEAD 2>/dev/null || echo none)
if git fetch -q origin main && git merge --ff-only -q origin/main; then
    after=$(git rev-parse HEAD)
    echo "[deploy] cod actualizat: ${before:0:7} -> ${after:0:7}"
else
    after=$before
    # Mediu local modificat pe server: continuăm cu ce e acolo, dar spunem clar.
    echo "[deploy] ATENȚIE: git pull a eșuat — rulez cu versiunea locală ($before)" >&2
fi

# S-a schimbat ceva în căile date între commit-ul de dinainte și cel de acum?
changed() {
    [[ "$before" != "$after" ]] || return 1
    # Fără istoric git (primul deploy): presupunem că totul e nou.
    [[ "$before" != none ]] || return 0
    ! git diff --quiet "$before" "$after" -- "$@" 2>/dev/null
}

# Frontend-ul nu face parte din binarul nativ: e un build Vite servit de nginx.
# Fără pasul ăsta, modificările de UI nu ajung pe server nici prin deploy manual,
# nici prin timer.
publish_frontend() {
    local dir="$REPO/frontend" web="$REPO/runtime/web"
    if [[ ! -f "$dir/package.json" ]]; then
        echo "[deploy] ATENȚIE: $dir/package.json lipsește — sar peste frontend" >&2
        return 1
    fi
    if ! command -v npm >/dev/null 2>&1; then
        echo "[deploy] ATENȚIE: npm lipsește pe server — sar peste frontend" >&2
        return 1
    fi
    echo "[deploy] build frontend"
    if ! ( cd "$dir" && npm ci --no-audit --no-fund --loglevel=error && npm run build ); then
        echo "[deploy] EROARE: build-ul de frontend a eșuat — păstrez build-ul anterior" >&2
        return 1
    fi
    mkdir -p "$web"
    if ! rsync -a --delete "$dir/dist/" "$web/"; then
        echo "[deploy] EROARE: copierea build-ului în $web a eșuat" >&2
        return 1
    fi
    # nginx citește ca utilizatorul serviciului, nu ca root.
    chown -R toonmint:toonmint "$web" 2>/dev/null || true
    chmod -R u=rwX,go=rX "$web" 2>/dev/null || true
    echo "[deploy] frontend publicat"
}

if [[ $SKIP_FRONTEND -eq 1 ]]; then
    echo "[deploy] frontend: sar peste (--skip-frontend)"
elif changed frontend || [[ ! -f "$REPO/runtime/web/index.html" ]]; then
    publish_frontend
else
    echo "[deploy] frontend: neschimbat"
fi

# Repornim aplicația doar dacă s-a schimbat ceva din binar sau din launcher;
# un deploy care aduce doar frontend nu are de ce să restarteze serviciul.
extra=()
if changed backend runtime .github; then
    extra+=(--force)
fi

exec "$REPO/runtime/deploy-native.sh" "${arguments[@]}" "${extra[@]}"
