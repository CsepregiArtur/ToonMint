#!/usr/bin/env bash
# Punctul de intrare pentru deploy-ul automat (toonmint-deploy.timer):
# actualizează codul, apoi instalează ultimul binar nativ.
#
#   ./deploy.sh                 # git pull + deploy-native.sh --latest
#   ./deploy.sh --tag native-x  # git pull + un release anume
#   ./deploy.sh --rollback      # git pull + revine la binarul anterior
#
# Pull-ul e cel care aduce run.sh și deploy-native.sh la zi; fără el, timer-ul ar
# rula mereu versiunea veche a scripturilor.
set -uo pipefail

REPO="${TOONMINT_REPO:-/root/ToonMint}"
cd "$REPO" || exit 1

arguments=("$@")
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

# Scripturile pot fi printre fișierele aduse de pull; în cazul ăsta repornim
# aplicația chiar dacă binarul e identic, altfel run.sh nou nu s-ar aplica.
extra=()
[[ "$before" != "$after" ]] && extra+=(--force)

exec "$REPO/runtime/deploy-native.sh" "${arguments[@]}" "${extra[@]}"
