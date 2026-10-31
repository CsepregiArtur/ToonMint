# 11 – Fiscalitate: persoană fizică, PFA sau SRL, și TVA/OSS

> ⚠️ Nu este consultanță fiscală. Pragurile și salariul minim se schimbă anual, iar
> interpretarea depinde de situația ta concretă. Cifrele de mai jos sunt cele din
> 2024–2025 — **verifică valorile pentru anul curent** cu un contabil sau pe anaf.ro
> înainte de a lua o decizie.

## 1. Corecția importantă: nu există „prag până la care poți vinde ca persoană fizică”

Este o confuzie frecventă. Realitatea:

- **OUG 44/2008 art. 3**: activitatea economică desfășurată **în mod obișnuit** (repetitiv,
  cu scop de profit) se face **doar** prin PFA / II / IF / SRL.
- **Art. 4** din același act permite explicit doar activitatea **ocazională** fără nicio
  formă de organizare (vânzarea unui lucru din gospodărie, o prestație singulară).
- Un abonament lunar vândut global **nu este ocazional**, deci nu există un plafon de
  lei sub care să funcționeze „ca persoană fizică”.

Ce **există** ca praguri — dar ele se referă la altceva:

| Prag | La ce se referă | Consecință |
|---|---|---|
| **10.000 € / an** (total UE) | TVA pentru servicii electronice B2C | sub el poți aplica regula țării tale; peste el → OSS |
| **395.000 lei / an** | înregistrare obligatorie în scopuri de TVA în România | peste el devii plătitor de TVA |
| **6 / 12 / 24 salarii minime** | baza CASS / CAS la PFA | contribuții de sănătate și pensie |
| **60.000 € / an** | plafon regim microîntreprindere (SRL) | peste el → impozit pe profit 16% |

Deci: „până la o limită pot ca persoană fizică, după aceea PFA/SRL” → **fals**.
Corect: „de la primul leu de activitate obișnuită am nevoie de o formă de organizare;
pragurile decid doar cât plătesc și ce declar”.

## 2. PFA (sau II / IF)

Impozitare în sistem real (norma de venit nu mai e o opțiune pentru cele mai multe coduri CAEN):

| Element | Regulă (2024–2025) |
|---|---|
| Impozit pe venit | **10%** din venitul net (venituri − cheltuieli deductibile) |
| CAS (pensie) | **25%**; devine obligatoriu când venitul net ≥ **12 salarii minime** (bază = 12 salarii; poți opta pentru 24) |
| CASS (sănătate) | **10%** dacă venitul net ≥ **6 salarii minime**; baza se alege pe trepte: 6 / 12 / 24 salarii |
| Declarare | Declarația unică (D212), anual, pentru venitul realizat + estimarea anului curent |
| TVA | poate fi plătitor de TVA; poate face **OSS** |
| Costuri | contabilitate redusă (poți ține singur evidența în partidă simplă) |
| Răspundere | **nelimitată** — răspunzi cu patrimoniul personal |

Exemplu orientativ (salariul minim 2025 = 4.050 lei; 6 salarii = 24.300 lei, 12 salarii = 48.600 lei):
un venit net de 60.000 lei → impozit 10% (6.000 lei) + CASS 10% pe baza de 12 salarii
(4.860 lei) + CAS 25% pe 48.600 lei (12.150 lei).

## 3. SRL cu regim micro

| Element | Regulă (2024–2025) |
|---|---|
| Impozit micro | **1%** din venituri, dacă venituri ≤ **60.000 €** și ai **cel puțin un salariat** |
| Peste plafon | impozit pe profit **16%** |
| Dividende | **8%** la distribuire + CASS dacă depășește pragul de 6/12/24 salarii |
| Costuri | contabilitate ~200–400 lei/lună + salariul minim brut pentru angajat |
| Răspundere | **limitată** la capitalul social |

Atenție: regimul micro se pierde dacă depășești plafonul sau condițiile (salariat,
ponderea veniturilor din consultanță/management).

## 4. PFA sau SRL pentru un SaaS mic?

| Criteriu | PFA | SRL micro |
|---|---|---|
| Profit net la ~50.000 €/an | mai mic (impozit 10%, dar CAS+CASS pe venit) | de obicei mai mic **dacă** reinvestești; mai mare dacă scoți banii ca dividende |
| Banii în buzunar lunar | direct | prin dividende (trimestrial/anual) sau salariu |
| Răspundere | personală | limitată |
| Clienți B2B / parteneri (RevenueCat, Google, Meta) | acceptat, dar unii cer entitate juridică | preferat |
| Administrare | minimă | contabil + salariat |
| Credibilitate | medie | mai mare (factură cu CUI, site-ul afișează date de firmă) |

Regulă practică: **sub ~30–40.000 €/an net** PFA e mai simplu; **peste**, sau dacă ai
parteneri care cer firmă ori vrei răspundere limitată, SRL micro.

## 5. TVA și OSS — partea care contează cel mai mult pentru ToonMint

Serviciile electronice (credite, abonamente, generări) vândute **consumatorilor** din UE
se impozitează **la cota din țara clientului**, nu a ta.

1. **Prag 10.000 €/an — total UE**, nu per țară. Sub el poți aplica regula statului tău
   (dacă nu ești plătitor de TVA în RO, nu colectezi TVA).
2. **Peste 10.000 €** → **OSS** (One Stop Shop): o singură înregistrare în România, o
   declarație trimestrială, plătești TVA la cota fiecărei țări (19% RO, 21% ES, 25% DK…).
3. **Cine este vânzătorul?** — aici se decide totul:

| Cum vinzi | Cine colectează TVA-ul | Ce trebuie să faci |
|---|---|---|
| App Store / Google Play (IAP) | magazinul („deemed supplier”) | nimic pe TVA; raportezi doar venitul |
| **RevenueCat Web Purchase Link** cu Stripe/procesator propriu | **tu** | înregistrare **OSS** + TVA pe țara clientului |
| Merchant of Record (Paddle, Lemon Squeezy, FastSpring) | furnizorul MoR | nimic pe TVA; primești suma netă |

Configurația actuală a proiectului (`REVENUECAT_PURCHASE_LINK_URL` → `pay.rev.cat`) intră
pe rândul al doilea: **verifică în contul tău RevenueCat dacă ești tu vânzătorul de
record**. Dacă da, OSS-ul este responsabilitatea ta.

4. **Prețul afișat** trebuie să fie **total, cu TVA inclus**, pentru consumatorii UE
   (Directiva 2011/83/UE). Acum prețurile sunt în USD („$4.99 / week”): pentru clienții
   UE trebuie fie afișate cu TVA inclus, fie adăugat TVA-ul **vizibil** în checkout.

## 6. Ce înseamnă pentru ToonMint, concret

- **Varianta simplă (recomandată la start):** vinzi prin **App Store / Google Play** sau
  folosești un **merchant of record** → TVA/OSS nu mai e problema ta; rămâne doar
  impozitul pe venitul tău (PFA sau SRL).
- **Varianta cu checkout propriu (RevenueCat Web + Stripe):** ai nevoie de OSS de la
  primul euro peste 10.000 €/an cross-border, plus prețuri cu TVA inclus.
- **Forma de organizare:** pentru un serviciu care se vinde lunar, registrul e necesar
  din prima zi — PFA (simplu, ieftin) sau SRL micro (răspundere limitată, parteneri).

## 7. Dacă vinzi din Ungaria

Prima întrebare nu e fiscală, ci de fapt: **unde locuiești efectiv?**

- Dacă te muți în Ungaria (rezidență fiscală acolo: >183 zile sau centrul intereselor
  vitale) → regulile maghiare se aplică integral, inclusiv pentru venitul personal.
- Dacă stai în Satu Mare și doar **înființezi** o firmă în Ungaria → rămâi rezident fiscal
  român pentru venitul personal, iar firma poate fi considerată rezidentă în România
  după locul conducerii efective (*place of effective management*). Administrarea reală
  din România creează și risc de sediu permanent. **Nu este o soluție de „mutat taxele”.**

### Formele de organizare din Ungaria

| Formă | Impozit | Contribuții | Observații |
|---|---|---|---|
| **Egyéni vállalkozó (EV)** — PFA maghiar | SZJA **15%** pe profit | TB **18,5%**, baza minimă = salariul minim (30% pentru activitate secundară) | înregistrare la NAV + adresă (székhely) în HU; scutire TVA până la **12 mil. Ft/an**; impozit local (IPA) max 2% din venituri sau 50.000 Ft |
| **Kft.** | társasági adó **9%** | salarii: 13% szocho + 18,5% TB | capital minim 3 mil. Ft; dividende 15% SZJA + 13% szocho; contabil 20–40k Ft/lună + avocat la înființare |
| **KATA** (forfetar) | 50.000 Ft/lună, plafon **18 mil. Ft/an** | — | **nu se potrivește aici** — vezi mai jos |

TVA standard în Ungaria: **27%** (cea mai mare din UE). Pentru servicii electronice B2C
regulile sunt identice la nivel UE: prag **10.000 €/an total UE** → **OSS**, cu Ungaria ca
stat de identificare, și TVA la cota țării clientului.

### De ce KATA nu merge pentru ToonMint

KATA cere trei lucruri incompatibile cu un SaaS global:

1. **doar clienți persoane fizice** — nu poți factura Apple, Google sau RevenueCat;
2. **doar activitate principală**, fără alte venituri din activități independente;
3. **statut fără TVA** — iar peste 10.000 €/an de servicii electronice B2C ești obligat să
   te înregistrezi în scopuri de TVA și pentru OSS, ceea ce te scoate din KATA.

### Capcane la vânzarea „worldwide” cu checkout propriu

Pragul de 10.000 € este **doar pentru UE**. În afara UE există țări cu prag zero sau foarte
jos pentru comercianții ne-stabiliți acolo:

| Țară | Prag pentru servicii digitale B2C |
|---|---|
| Marea Britanie | **0** — înregistrare de la prima vânzare (post-Brexit) |
| Norvegia | ~50.000 NOK |
| Elveția | ~100.000 CHF |
| Australia | ~75.000 AUD |
| Canada, Japonia, Coreea de Sud, Africa de Sud | praguri proprii, înregistrare separată |

Un **merchant of record** (Paddle, Lemon Squeezy, FastSpring) preia toate aceste
înregistrări; altfel ajungi să gestionezi 5–10 înregistrări fiscale pentru câteva mii de euro.

### România vs Ungaria, pentru acest serviciu

| | România (PFA) | Ungaria (EV) |
|---|---|---|
| Impozit pe venit | 10% | 15% |
| Contribuții sociale | CAS 25% + CASS 10%, pe praguri de salarii minime | TB 18,5% din baza minimă lunară |
| TVA standard | 21% (din august 2025) | 27% |
| Prag scutire TVA | 395.000 lei | 12 mil. Ft |
| Impozit local | nu există | IPA până la 2% din venituri |
| Birocrație | declarație unică anuală | mai multe declarații la NAV |
| Cost lunar estimat | ~0 (autoevidență) | contabil 5–15k Ft |

Concluzie practică: **dacă rămâi în România, PFA + merchant of record este cea mai
ieftină și mai simplă combinație.** O firmă maghiară are sens doar dacă te muți efectiv
în Ungaria (sau ai acolo substanță reală: birou, angajați, decizii luate acolo).

## 8. Legături utile

- OUG 44/2008 (PFA/II/IF), Codul fiscal art. 67–69, 148, 170–171
- Regulamentul de punere în aplicare (UE) 282/2011 — art. 9a (vânzător prezumat)
- Directiva 2006/112/CE — art. 58, 59c (pragul de 10.000 €), 358a–369k (OSS)
- ghiduri ANAF: „Servicii electronice — OSS” și „Înregistrarea în scopuri de TVA”
- Ungaria: Szja tv. (2015. évi CXXVII.), Tbj. (2019. évi CXXII.), Áfa tv. (2007. évi
  CXXVII.), KATA tv. (2012. évi CXLVII.), 2017. évi CL. tv. (ordonanța fiscală) — și
  ghidurile NAV (nav.gov.hu), plus un contabil maghiar (*könyvelő*) înainte de orice decizie
