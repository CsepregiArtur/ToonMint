# 10 – Bookkeeping (încasări, costuri, marjă)

Registrul financiar răspunde la trei întrebări: **cât intră**, **cât costă** și **ce
marjă rămâne**. Este *management accounting* — pentru decizii de preț și profitabilitate —
nu contabilitate fiscală: documentele fiscale sunt emise de magazine (Apple, Google,
RevenueCat ca merchant of record).

UI-ul nu este încă implementat; toate datele sunt disponibile prin API-ul de admin
([02 – API](02-api.md)) și sunt gândite pentru un dashboard central viitor.

## 1. De ce un registru separat

`credit_transactions` este un ledger de **credite**, nu de bani: un abonament săptămânal
acordă 50 de credite o dată și se reînnoiește, iar valoarea unui credit diferă între
planuri. Sumele reale se scriu în `finance_entries`, cu o singură regulă: **suma este
mereu pozitivă, direcția vine din categorie**.

| Tabelă | Ce măsoară |
|---|---|
| `credit_transactions` | cine are dreptul la câte generări |
| `finance_entries` | câți bani au intrat și câți au ieșit |

## 2. Modelul de date

```text
finance_entries
  kind           INCOME | EXPENSE          (dedus din categorie)
  category       SALE, OTHER_INCOME, REFUND, STORE_FEE,
                 AI_GENERATION, HOSTING, DOMAIN, MARKETING, OTHER
  amount         NUMERIC(14,4), mereu > 0
  currency       ISO-4217 (3 litere)
  fx_rate        cursul către moneda de raportare, înghețat la înregistrare
  source         AUTO_JOB | AUTO_REVENUECAT | MANUAL
  reference_type JOB | REVENUECAT_EVENT | null
  reference_key  id-ul jobului / id-ul evenimentului RevenueCat
  product_id     produsul vândut (doar pentru încasări)
  occurred_at    momentul economic (generare, plată, cheltuială)
```

Indexul unic `(source, reference_type, reference_key, category)` este mecanismul de
idempotență: un job produce cel mult o linie `AI_GENERATION`, iar un eveniment
RevenueCat cel mult o linie `SALE`, una `STORE_FEE` și una `REFUND`.

## 3. Ce se scrie automat

### 3.1 Costul AI — la fiecare generare finalizată

`JobProcessor` apelează `FinanceService.recordGenerationCost(job)` după ce jobul devine
`COMPLETED`:

| Situație | Intrare |
|---|---|
| job normal finalizat | `EXPENSE / AI_GENERATION`, suma = `app.finance.cost-per-generation` |
| job de trial (fără cont) | idem, cu descriere „Trial generation” — este cost de achiziție |
| job eșuat | **nimic** (nu s-a livrat imagine) |

Costul este o configurație, nu o valoare citită din răspunsul Gemini: providerul nu
întoarce azi `usageMetadata`, iar estimarea per imagine este suficientă pentru marjă.
Valoarea se copiază în linie, deci o modificare ulterioară a configurării nu rescrie
istoricul.

### 3.2 Încasări — din webhook-ul RevenueCat

`RevenueCatService` scrie, în aceeași tranzacție cu acordarea creditelor:

| Eveniment | Intrare |
|---|---|
| `INITIAL_PURCHASE`, `RENEWAL`, `PRODUCT_CHANGE` | `INCOME / SALE` |
| `NON_RENEWING_PURCHASE` (pachet de credite) | `INCOME / SALE` |
| `REFUND` | `EXPENSE / REFUND` (scade din încasări) |
| `EXPIRATION`, `CANCELLATION`, `BILLING_ISSUE` | nimic |

Suma vine, în ordine, din:

1. `event.price` (sau `event.price_in_purchased_currency`) din payload;
2. în lipsa ei, din prima sumă a etichetei `app.revenuecat.products[].price`
   (ex. `$4.99 / week` → `4.99`).

Dacă nu se poate determina nicio sumă, plata **nu** este blocată: se scrie un warning în
log și se sare peste linia de venit.

Comisionul magazinului este înregistrat doar când payload-ul conține
`takehome_percentage` (fracție între 0 și 1): `STORE_FEE = price × (1 − takehome)`.

### 3.3 Cheltuieli manuale

Hosting, domeniu, marketing, contabilitate etc. se adaugă prin
`POST /api/admin/finance/entries`. Sunt singurele intrări șterse (`DELETE`), pentru
corecții; cele automate rămân imutabile ca raportul să rămână reconciliabil.

## 4. Cum se calculează raportul

Toate sumele sunt convertite în moneda de raportare: `amount × fx_rate`.

```text
grossRevenue  = SALE + OTHER_INCOME
refunds       = REFUND
storeFees     = STORE_FEE
netRevenue    = grossRevenue − refunds − storeFees

aiCost        = AI_GENERATION
otherCost     = HOSTING + DOMAIN + MARKETING + OTHER

grossMargin   = netRevenue − aiCost          ← după costul direct (AI)
netProfit     = grossMargin − otherCost      ← după costurile operaționale
marginRatio   = grossMargin / netRevenue
```

Raportul include și:

- `generations` — numărul de linii `AI_GENERATION` din interval (generări facturate);
- `costPerGeneration` — costul configurat, util pentru simulări;
- `revenueByProduct` — încasările și numărul de plăți pe produs RevenueCat;
- `monthly` — aceleași cifre defalcate pe lună (`YYYY-MM`).

Costul pe stil nu este în raport: se obține combinând `GET /api/jobs/stats` (joburi
finalizate pe stil) cu `costPerGeneration`.

## 5. Configurare

```yaml
app:
  finance:
    reporting-currency: ${FINANCE_REPORTING_CURRENCY:USD}
    cost-per-generation: ${FINANCE_COST_PER_GENERATION:0.034}
    fx-rates: {}       # ex. EUR: 1.08 (1 EUR = 1.08 USD)
```

Cursurile pot fi setate și prin mediu, prin relaxed binding:
`APP_FINANCE_FX_RATES_EUR=1.08`. O monedă fără curs configurat folosește `1` și produce
un warning în log — corect pentru sume în moneda de raportare, greșit pentru altele,
deci completează harta înainte de a vinde în altă monedă.

## 6. Contractul pentru UI-ul central (viitor)

| Nevoie în UI | Endpoint |
|---|---|
| carduri KPI + grafic lunar | `GET /api/admin/finance/summary?from=&to=` |
| tabel de intrări, filtrabil | `GET /api/admin/finance/entries?category=&from=&to=&page=&size=` |
| formular „adaugă cheltuială” | `POST /api/admin/finance/entries` |
| corecție/ștergere cheltuială | `DELETE /api/admin/finance/entries/{id}` |

Toate cer autentificare cu rol `ADMIN`. Exemplele de request/response sunt în
[02 – API, secțiunea 8](02-api.md).

## 7. Reconciliere și limitări cunoscute

- **Idempotență** — un webhook retrimis nu dublează venitul; un job reprocesat nu
  dublează costul. Verificare rapidă: `SUM(amount)` pe `AI_GENERATION` trebuie să fie
  egal cu numărul de joburi `COMPLETED` × costul configurat (dacă nu s-a schimbat costul).
- **Sumele din magazine nu sunt payout-uri** — RevenueCat raportează prețul plătit de
  client; diferența până la suma virată efectiv (comision, taxe) apare doar în rapoartele
  de payout ale magazinului. `STORE_FEE` aproximează comisionul când payload-ul îl conține.
- **Costul AI este o estimare** per imagine, nu factura Google; pentru cost exact ar
  trebui parsat `usageMetadata` din răspunsul Gemini.
- **Monede** — conversia folosește cursul configurat la momentul înregistrării, nu un
  curs istoric real.
- **Retry-uri** — un job cu mai multe încercări produce o singură linie de cost, la succes.
