# 04 – Email prin Google (Gmail SMTP)

Backendul trimite emailuri cu Spring Boot Mail. În producție, expeditorul este un cont
Google, iar autentificarea folosește o **parolă de aplicație** (nu parola contului).

## 1. Pregătirea contului Google

1. Activează verificarea în doi pași pe contul Google
   (Cont Google → Securitate → Verificare în doi pași).
2. Deschide <https://myaccount.google.com/apppasswords>.
3. Creează o parolă de aplicație (ex. „ToonMint SMTP”) — primești 16 caractere.
4. Copiază parola imediat: nu mai poate fi afișată din nou.

> Dacă folosești Google Workspace, administratorul poate restricționa parolele de
> aplicație. În acest caz, folosește un cont de serviciu dedicat sau un provider
> SMTP tranzacțional — codul rămâne același.

## 2. Configurare

Variabile de mediu (profil `prod`):

```bash
MAIL_ENABLED=true
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=toonmint.noreply@gmail.com
MAIL_PASSWORD=abcdefghijklmnop      # parola de aplicație, 16 caractere
MAIL_FROM_NAME=ToonMint
```

`application-prod.yml` setează deja:

- port `587` cu **STARTTLS** obligatoriu;
- autentificare SMTP;
- timeout-uri de 5 secunde (conexiune, citire, scriere).

În dezvoltare (`MAIL_ENABLED=false`), emailurile **nu** pleacă: sunt doar logate, iar
răspunsul endpointului are `"simulated": true`.

## 3. Utilizare

Doar operatorii pot trimite emailuri:

```bash
curl -X POST http://localhost:8080/api/admin/send-email \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "userIds": [1, 2],
    "emails": ["client@example.com"],
    "subject": "Un stil nou în ToonMint",
    "text": "Salut! Am adăugat stilul Comic book."
  }'
```

```json
{ "requested": 3, "sent": 3, "failed": 0, "simulated": false, "failures": [] }
```

Fiecare trimitere este înregistrată în `email_messages` (cine a trimis, câți
destinatari, status `SENT` / `PARTIAL` / `FAILED` / `SIMULATED`, erori).

Limite și reguli implementate:

- adresele din `userIds` și `emails` sunt deduplicate;
- maximum 200 de destinatari per cerere (`400 BAD_REQUEST` peste limită);
- fiecare mesaj este trimis individual, astfel încât un destinatar invalid să nu
  blocheze restul; eșecurile sunt raportate în `failures`.

## 4. Emailuri tranzacționale: coduri OTP

Aplicația trimite automat coduri de 6 cifre pentru:

- confirmarea adresei de email după înregistrare (`EMAIL_VERIFICATION`);
- resetarea parolei (`PASSWORD_RESET`).

Reguli implementate în `VerificationCodeService` / `EmailVerificationMailer`:

- codul expiră în **15 minute** și poate fi folosit o singură dată;
- maximum **5 încercări greșite** per cod, apoi codul este invalidat;
- un cod nou se poate cere după **60 de secunde** (`429 CODE_COOLDOWN`);
- codurile sunt stocate doar ca hash BCrypt în `verification_codes`, niciodată în clar;
- emailurile cu coduri **nu** sunt salvate în `email_messages` (ar însemna coduri în clar în DB);
- livrarea are loc după commit-ul tranzacției, ca să nu plece coduri pentru tranzacții anulate.

Când `MAIL_ENABLED=false` (dezvoltare), emailul nu pleacă: codul apare în logul
backendului ca `WARN` (`... code for user@example.com is 123456`), deci fluxul poate fi
testat local fără SMTP.

## 5. Limitări Gmail

| Cont | Limita orientativă |
|---|---|
| Gmail gratuit | ~500 destinatari / 24h |
| Google Workspace | ~2000 destinatari / 24h |

Pentru campanii mari, recomandarea este un provider tranzacțional (SendGrid, Mailgun,
Amazon SES) sau Google Workspace cu un domeniu propriu. Schimbarea se face doar prin
configurație (`MAIL_HOST`, `MAIL_PORT`, credențiale) — codul nu depinde de Gmail.

## 6. Probleme frecvente

| Eroare | Cauză | Soluție |
|---|---|---|
| `535-5.7.8 Username and Password not accepted` | parolă de cont în loc de parolă de aplicație | generează o parolă de aplicație |
| `534-5.7.9 Application-specific password required` | 2FA activat, dar lipsește parola de aplicație | creează parola de aplicație |
| `550-5.7.1 From address not owned` | `MAIL_USERNAME` diferă de expeditor | folosește aceeași adresă (sau un alias verificat) |
| Timeout la trimitere | rețea/firewall blochează 587 | permite ieșirea pe 587 sau folosește 465 + SSL |
| `MAIL_ENABLED=false` și nu pleacă nimic | mod simulat | setează `MAIL_ENABLED=true` |

## 7. Securitate

- Nu comite niciodată parola de aplicație: `.env` este în `.gitignore`.
- Revocă parola de aplicație din contul Google dacă este expusă; nu afectează parola
  principală.
- Folosește un cont dedicat (`no-reply@`), nu contul personal al administratorului.
- Endpoint-ul de email este protejat de rolul `ADMIN`; verifică logurile din
  `email_messages` pentru abuz.
