# Deploy ToonMint

Server: `192.168.1.99` (`App-Hosting`), acces prin cheie SSH, user `root`.
Aplicația rulează **doar pe loopback**; traficul public intră prin **Cloudflare Tunnel**
(`cloudflared` → `127.0.0.1:8088` → nginx → SPA + proxy `/api/` → Spring Boot `:8080`).

| Componentă | Locație pe server |
|---|---|
| Binar nativ (preferat) | `/root/ToonMint/runtime/bin/toonmint-backend` |
| Jar backend (fallback JVM) | `/root/ToonMint/runtime/app.jar` |
| Frontend (build Vite) | `/root/ToonMint/runtime/web/` |
| Variabile de mediu | `/etc/toonmint/toonmint.env` |
| Serviciu | `toonmint-api.service`, `ExecStart=/root/ToonMint/runtime/run.sh` (`systemctl restart toonmint-api`) |
| nginx | `/etc/nginx/sites-available/toonmint.conf` (symlink în `sites-enabled`) |
| Snippet headere | `/etc/nginx/snippets/toonmint-security-headers.conf` |

---

## 1. Backend

CI publică un release la fiecare push pe `main`: tag `native-<sha>`, asset
`toonmint-backend-linux-amd64`. `run.sh` preferă automat binarul nativ dacă există
în `bin/`, altfel cade pe `app.jar`, deci trecerea JVM → nativ nu schimbă unit-ul
systemd.

### 1.1 Nativ (recomandat)

Scripturile se actualizează din repo (nu vin cu release-ul), deci întâi un pull:

```bash
cd /root/ToonMint && git pull
```

Apoi, ca root:

```bash
/root/ToonMint/runtime/deploy-native.sh --latest          # ultimul release
/root/ToonMint/runtime/deploy-native.sh --tag native-<sha> # un release anume
/root/ToonMint/runtime/deploy-native.sh --rollback         # revine la versiunea anterioară
```

Scriptul validează că fișierul e ELF și are mărime plauzibilă, păstrează binarul
curent ca `bin/toonmint-backend.prev`, repornește `toonmint-api` și face health
check pe `127.0.0.1:8080/actuator/health`; dacă health check-ul pică, revine singur
la versiunea anterioară. Sare peste restart dacă binarul descărcat e identic.

Automatizare opțională — serverul trage singur ultimul release la fiecare 10 minute:

```bash
scp deploy/systemd/toonmint-deploy.service deploy/systemd/toonmint-deploy.timer \
  192.168.1.99:/etc/systemd/system/
ssh 192.168.1.99 'systemctl daemon-reload && systemctl enable --now toonmint-deploy.timer'
journalctl -u toonmint-deploy -n 20 --no-pager   # ce a făcut fiecare rulare
```

### 1.2 JVM (fallback)

```bash
cd backend
mvn -DskipTests package                       # → target/toonmint-backend-<versiune>.jar

ssh 192.168.1.99 'cp -a /root/ToonMint/runtime/app.jar \
  /root/ToonMint/runtime/app.jar.bak.$(date +%Y%m%d-%H%M%S)'

scp target/toonmint-backend-<versiune>.jar 192.168.1.99:/root/ToonMint/runtime/app.jar.new

ssh 192.168.1.99 'set -e
  chown toonmint:toonmint /root/ToonMint/runtime/app.jar.new
  chmod 640               /root/ToonMint/runtime/app.jar.new
  cd /root/ToonMint/runtime && mv app.jar.new app.jar
  systemctl restart toonmint-api
  curl --retry 20 --retry-delay 1 --retry-connrefused \
    -s -o /dev/null -w "api=%{http_code}\n" http://127.0.0.1:8080/api/styles
  journalctl -u toonmint-api -n 20 --no-pager | tail -20'
```

**Rollback:** `mv app.jar.bak.<timestamp> app.jar && systemctl restart toonmint-api`.

> ⚠️ Numele jar-ului conține versiunea din `pom.xml`. După un bump, actualizează comanda.
>
> ⚠️ Cât timp există `bin/toonmint-backend`, `run.sh` ignoră `app.jar`. Ca să revii
> pe JVM, șterge binarul: `rm /root/ToonMint/runtime/bin/toonmint-backend`.

## 2. Frontend

```bash
cd frontend && npm run build
rsync -az --delete dist/ 192.168.1.99:/root/ToonMint/runtime/web/
```

`index.html` nu se cachează (`no-cache, must-revalidate`), iar `/assets/*` are hash →
`immutable, 1 an`. Deci un deploy nou se vede imediat, fără hard-reload.

## 3. nginx — header-e de securitate

Fișierele sursă sunt în acest director:

- `toonmint-security-headers.conf` → se copiază în `/etc/nginx/snippets/`
- `patch-security-headers.py` → inserează `include` în cele 3 locații SPA
  (`/assets/`, `/`, `= /index.html`). Idempotent.

```bash
scp deploy/nginx/toonmint-security-headers.conf 192.168.1.99:/etc/nginx/snippets/
scp deploy/nginx/patch-security-headers.py     192.168.1.99:/tmp/
ssh 192.168.1.99 'set -e
  cp -a /etc/nginx/sites-available/toonmint.conf \
        /etc/nginx/sites-available/toonmint.conf.bak.$(date +%Y%m%d-%H%M%S)
  python3 /tmp/patch-security-headers.py
  nginx -t && systemctl reload nginx'
```

De ce `include` și nu `add_header` în `server`: nginx moștenește `add_header` din
nivelul superior **doar** dacă locația nu are propriile directive. `/assets/` și
`= /index.html` au `add_header Cache-Control`, deci ar pierde headerele globale.

HSTS-ul e dat tot din nginx (`max-age=15552000`, fără `includeSubDomains`), nu din
Cloudflare — Cloudflare lasă headerul de la origine să treacă.

## 4. Verificare rapidă după deploy

```bash
# header-e
curl -sI https://toonmint.cs-hub.xyz/ | grep -iE "strict-transport|x-frame|x-content-type|referrer|permissions"

# rate limiting pe login (al 11-lea = 429)
for i in $(seq 1 11); do
  curl -s -o /dev/null -w "%{http_code} " -X POST https://toonmint.cs-hub.xyz/api/auth/login \
    -H "Content-Type: application/json" -d '{"email":"nobody@example.com","password":"x"}'
done; echo

# webhook (401 = aplicația răspunde, tokenul e configurat)
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://toonmint.cs-hub.xyz/api/webhooks/revenuecat \
  -H "Content-Type: application/json" -d '{"event":{"id":"probe","type":"TEST"}}'
```

> `curl` către rădăcină sau `/api/*` poate primi `403` de la Cloudflare (bot protection).
> Excepția există doar pentru `/api/webhooks/revenuecat`. Pentru restul, testează din browser.
