#!/usr/bin/env bash
# Verificare lanț auto-deploy ToonMint. Rulat pe server, ca root. Nu modifică nimic.
set -uo pipefail

REPO=/root/ToonMint
CUR=$REPO/runtime/bin/toonmint-backend
PREV=$REPO/runtime/bin/toonmint-backend.prev
API=toonmint-api
DEP=toonmint-deploy
PASS=0; FAIL=0; WARN=0
ok(){ printf 'PASS  %s\n' "$*"; PASS=$((PASS+1)); }
no(){ printf 'FAIL  %s\n' "$*"; FAIL=$((FAIL+1)); }
wa(){ printf 'WARN  %s\n' "$*"; WARN=$((WARN+1)); }
q(){ "$@" >/dev/null 2>&1; }

echo "=== 1. Cod pe server ==="
q test -d "$REPO/.git" && ok "checkout git în $REPO" || no "$REPO nu e checkout git"
br=$(git -C "$REPO" rev-parse --abbrev-ref HEAD 2>/dev/null)
[ "$br" = main ] && ok "branch main" || no "branch este '$br', nu main"
dirty=$(git -C "$REPO" status --porcelain 2>/dev/null)
[ -z "$dirty" ] && ok "arbore curat" || no "modificări locale (pull-ul poate eșua): $(echo "$dirty" | head -5 | tr '\n' ' ')"
q test -x "$REPO/runtime/deploy.sh" && ok "runtime/deploy.sh executabil" || no "runtime/deploy.sh lipsește sau nu e executabil"
q test -x "$REPO/runtime/deploy-native.sh" && ok "runtime/deploy-native.sh executabil" || no "deploy-native.sh lipsește sau nu e executabil"
q test -x "$REPO/runtime/run.sh" && ok "runtime/run.sh executabil" || no "run.sh lipsește sau nu e executabil"

echo
echo "=== 2. Pull automat posibil ==="
if q git -C "$REPO" fetch --dry-run -q origin main; then
    ok "git fetch merge (repo public, fără credențiale)"
else
    no "git fetch eșuează — DNS/rețea/remote"
fi
git -C "$REPO" fetch -q origin main 2>/dev/null
local_rev=$(git -C "$REPO" rev-parse HEAD)
remote_rev=$(git -C "$REPO" rev-parse origin/main)
if [ "$local_rev" = "$remote_rev" ]; then
    ok "la zi cu origin/main (${local_rev:0:7})"
else
    wa "în urmă: local ${local_rev:0:7} vs origin ${remote_rev:0:7} (următorul tick îl aduce)"
fi

echo
echo "=== 3. systemd ==="
q systemctl cat $DEP.service && ok "$DEP.service există" || no "$DEP.service lipsește"
systemctl cat $DEP.service 2>/dev/null | grep -q "ExecStart=$REPO/runtime/deploy.sh" \
    && ok "ExecStart -> runtime/deploy.sh" \
    || no "ExecStart NU pointează la $REPO/runtime/deploy.sh (vezi: systemctl cat $DEP.service)"
q systemctl is-enabled --quiet $DEP.timer && ok "$DEP.timer enabled" || no "$DEP.timer NU e enabled (nu va rula automat)"
q systemctl is-active --quiet $DEP.timer && ok "$DEP.timer active" || no "$DEP.timer NU e activ"
systemctl list-timers $DEP.timer --no-pager 2>/dev/null | grep -q "$DEP" \
    && ok "următoarea rulare programată" || no "timer-ul nu apare în list-timers"
res=$(systemctl show -p Result --value $DEP.service 2>/dev/null)
[ "$res" = success ] && ok "ultima rulare: success" || wa "ultima rulare: '$res' (verifică journalctl)"

echo
echo "=== 4. Binar instalat ==="
if [ -f "$CUR" ]; then
    magic=$(head -c4 "$CUR" | od -An -tx1 | tr -d ' \n')
    [ "$magic" = 7f454c46 ] && ok "binar ELF valid" || no "binarul nu e ELF (magic=$magic)"
    size=$(stat -c%s "$CUR")
    [ "$size" -ge 20971520 ] && ok "mărime $((size/1048576)) MB" || no "mărime suspectă: $size B"
    [ -x "$CUR" ] && ok "binar executabil" || no "binarul nu e executabil"
    echo "      sha256 $(sha256sum "$CUR" | cut -c1-16)…  instalat $(stat -c%y "$CUR" | cut -d. -f1)"
else
    no "$CUR lipsește — încă rulează JVM"
fi
[ -x "$PREV" ] && ok "rollback disponibil (bin/toonmint-backend.prev)" || wa "fără .prev — rollback indisponibil"

echo
echo "=== 5. Ce rulează de fapt ==="
cmd=$(systemctl show -p ExecStart --value $API 2>/dev/null | grep -o '/[^ ;]*' | head -1)
case "$cmd" in
    "$REPO/runtime/run.sh") ok "toonmint-api rulează run.sh (alege nativ dacă există)" ;;
    *) wa "ExecStart al $API este '$cmd', nu runtime/run.sh" ;;
esac
if pgrep -af 'toonmint-backend' >/dev/null 2>&1; then
    ok "proces nativ activ: $(pgrep -af toonmint-backend | head -1 | cut -c1-70)"
else
    no "nu rulează binarul nativ (probabil JVM)"
fi

echo
echo "=== 6. Aplicația răspunde ==="
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:8080/actuator/health)
[ "$code" = 200 ] && ok "/actuator/health -> 200" || no "/actuator/health -> $code"
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:8080/api/styles)
[ "$code" = 200 ] && ok "/api/styles -> 200" || no "/api/styles -> $code"

echo
echo "=== 7. Istoric deploy ==="
if [ -f /var/log/toonmint-deploy.log ]; then
    echo "      ultimele linii:"
    tail -4 /var/log/toonmint-deploy.log | sed 's/^/      /'
    grep -q 'DEPLOY OK' /var/log/toonmint-deploy.log && ok "log conține 'DEPLOY OK'" || wa "niciun 'DEPLOY OK' în log"
else
    wa "/var/log/toonmint-deploy.log lipsește (niciun deploy prin script încă)"
fi
echo "      ultimele 5 rulări systemd:"
journalctl -u $DEP -n 5 --no-pager -o cat 2>/dev/null | sed 's/^/      /' || true

echo
echo "=== REZULTAT: $PASS PASS, $FAIL FAIL, $WARN WARN ==="
[ "$FAIL" -eq 0 ] || exit 1
