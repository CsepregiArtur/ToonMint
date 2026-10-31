import { SITE } from './site'
import { DOCUMENTS_EN } from './documents.en'

// ---------------------------------------------------------------------------
// Textele documentelor legale, în română, ca structuri de blocuri.
// Blocurile sunt randate de `LegalView.vue`; în text poți folosi linkuri în
// formatul [etichetă](https://exemplu.ro).
//
// Textele sunt un punct de plecare corect din punct de vedere structural, dar
// NU țin loc de consultanță juridică. Completează toate `TODO`-urile din
// `site.js` și cere unui jurist o verificare înainte de lansare.
// ---------------------------------------------------------------------------

const p = (text) => ({ type: 'p', text })
const ul = (items) => ({ type: 'ul', items })
const table = (head, rows) => ({ type: 'table', head, rows })
const note = (text) => ({ type: 'note', text })

const ENTITY = SITE.entity

// Identificarea furnizorului. CUI-ul și numărul ONRC apar doar dacă există
// (persoană fizică fără PFA/SRL nu le are).
const identity = () => {
  const lines = [`Denumire: ${ENTITY.name}`]
  if (ENTITY.cui) lines.push(`CUI / CIF: ${ENTITY.cui}`)
  if (ENTITY.regCom) lines.push(`Nr. Registrul Comerțului: ${ENTITY.regCom}`)
  lines.push(`Sediu social: ${ENTITY.address}`)
  lines.push(`Email: ${ENTITY.email}`)
  lines.push(`Telefon: ${ENTITY.phone}`)
  return ul(lines)
}

export const DOCUMENTS = {
  // -------------------------------------------------------------------------
  confidentialitate: {
    slug: 'confidentialitate',
    title: 'Politica de confidențialitate',
    intro:
      'Această politică explică ce date personale prelucrăm când folosești ToonMint, ' +
      'de ce le prelucrăm, cât timp le păstrăm și ce drepturi ai. Se aplică site-ului ' +
      `și contului de pe ${SITE.url}.`,
    sections: [
      {
        id: 'operator',
        heading: '1. Cine este operatorul',
        blocks: [
          p(
            'Operatorul datelor cu caracter personal este furnizorul serviciului ToonMint:'
          ),
          identity(),
          p(
            `Pentru orice întrebare legată de datele tale ne poți scrie la ${SITE.privacyEmail}. ` +
              'Răspundem în termen de cel mult 30 de zile de la primirea cererii.'
          )
        ]
      },
      {
        id: 'date',
        heading: '2. Ce date prelucrăm',
        blocks: [
          table(
            ['Categorie', 'Exemple', 'Sursă'],
            [
              [
                'Date de cont',
                'adresă de email, nume afișat, parolă stocată ca hash BCrypt, rol (utilizator/operator), data înregistrării și a confirmării emailului',
                'tu, la creare cont'
              ],
              [
                'Date de autentificare',
                'adresa IP la înregistrare/autentificare, momentul cererilor, coduri OTP de 6 cifre (valabile 15 minute)',
                'generare automată'
              ],
              [
                'Fotografii încărcate',
                'imaginea pe care o trimiți pentru transformare',
                'tu, la generare'
              ],
              [
                'Imagini generate',
                'rezultatul toon, ținut în cache temporar pentru descărcare',
                'generare automată'
              ],
              [
                'Metadate job',
                'stilul ales, statusul, durata, dimensiunea fișierului, numărul de credite consumate',
                'generare automată'
              ],
              [
                'Date de facturare',
                'identificatorul tranzacției și al produsului primit de la procesatorul de plăți, istoricul de credite. Nu primim și nu stocăm numărul cardului.',
                'procesatorul de plăți (RevenueCat, App Store, Google Play)'
              ],
              [
                'Corespondență',
                'mesajele pe care ni le trimiți pe email sau prin formularul de contact',
                'tu'
              ]
            ]
          ),
          p('Nu prelucrăm date din categorii speciale (art. 9 GDPR) și nu cerem date suplimentare față de cele necesare serviciului.')
        ]
      },
      {
        id: 'scopuri',
        heading: '3. De ce le prelucrăm și pe ce bază legală',
        blocks: [
          table(
            ['Scop', 'Temei juridic', 'Detalii'],
            [
              [
                'Crearea și administrarea contului, autentificare, confirmare email, resetare parolă',
                'executarea contractului — art. 6(1)(b) GDPR',
                'fără aceste date nu putem livra serviciul'
              ],
              [
                'Generarea imaginilor și livrarea rezultatului',
                'executarea contractului — art. 6(1)(b)',
                'fotografia este trimisă modelului AI doar pentru a produce rezultatul cerut'
              ],
              [
                'Facturare, credite, abonamente, prevenirea fraudei la plăți',
                'executarea contractului — art. 6(1)(b); obligație legală — art. 6(1)(c) pentru documentele contabile',
                'sumele și documentele fiscale se păstrează 10 ani'
              ],
              [
                'Limitarea abuzurilor (rate limiting, o generare gratuită per email, securitatea contului)',
                'interes legitim — art. 6(1)(f)',
                'interesul de a menține serviciul disponibil și plătit corect'
              ],
              [
                'Măsurare a traficului și statistici (Google Analytics, Microsoft Clarity)',
                'consimțământ — art. 6(1)(a) și art. 129 din Legea 190/2018',
                'se activează numai după ce apeși „Accept toate” în banner'
              ],
              [
                'Publicitate (Google AdSense), dacă este activată',
                'consimțământ — art. 6(1)(a)',
                'nu se încarcă scriptul de publicitate fără consimțământ'
              ],
              [
                'Comunicări tranzacționale (cod de verificare, confirmări)',
                'executarea contractului — art. 6(1)(b)',
                'nu sunt marketing; nu pot fi dezactivate separat'
              ]
            ]
          ),
          p('Marketingul prin email (dacă îl vom introduce) se face exclusiv pe bază de consimțământ separat, cu dezabonare la fiecare mesaj.')
        ]
      },
      {
        id: 'fotografii',
        heading: '4. Fotografiile tale și prelucrarea cu AI',
        blocks: [
          p(
            'Fotografiile sunt stocate doar temporar: în momentul în care încărcarea ajunge pe server, ' +
              'imaginea intră într-un cache cu durată scurtă de viață, iar după generare și descărcare ' +
              'este ștearsă automat. În baza de date rămân doar metadatele jobului (stil, status, dată), ' +
              'nu conținutul imaginii.'
          ),
          p(
            'Pentru generare, imaginea este transmisă furnizorului de modele AI (Google — Gemini API). ' +
              'Conform termenilor Google pentru API-ul plătit, conținutul trimis prin API nu este folosit ' +
              'pentru antrenarea modelelor. Transferul se face prin conexiune criptată.'
          ),
          note(
            'Dacă încarci o fotografie în care apar și alte persoane, trebuie să ai acordul lor. ' +
              'Nu încărca fotografii ale minorilor, documente de identitate sau imagini care nu îți aparțin.'
          ),
          p(
            'Rezultatul generat este marcat ca fiind creat cu AI, iar în interfață și în metadatele ' +
              'descărcării este indicată originea sa artificială (art. 50 din Regulamentul (UE) 2024/1689 — AI Act).'
          )
        ]
      },
      {
        id: 'destinatari',
        heading: '5. Cine mai are acces la date',
        blocks: [
          p('Nu vindem datele tale. Le punem la dispoziție doar următorilor împuterniciți, strict pentru serviciul comandat:'),
          table(
            ['Furnizor', 'Rol', 'Date'],
            [
              ['Google (Gemini API)', 'generare imagini', 'fotografia încărcată și promptul stilului'],
              ['Google (Analytics, Clarity, AdSense)', 'statistici și publicitate', 'identificatori de browser și pagini vizitate — doar cu consimțământ'],
              ['RevenueCat și magazinele de aplicații', 'procesare plăți și abonamente', 'identificatorul de client, produsul, statusul abonamentului'],
              ['Google (Gmail SMTP)', 'trimiterea emailurilor tranzacționale', 'adresa de email și conținutul mesajului'],
              ['Furnizorul de găzduire și de baze de date', 'găzduire aplicație, PostgreSQL, Redis', 'toate datele de mai sus, în calitate de împuternicit']
            ]
          ),
          p(
            'Unii furnizori sunt în afara Spațiului Economic European (în special în SUA). Transferul se ' +
              'bazează pe decizia de adecvare pentru EU–US Data Privacy Framework și/sau pe clauzele ' +
              'contractuale standard aprobate de Comisia Europeană. Poți cere detalii despre garanțiile ' +
              `aplicate scriind la ${SITE.privacyEmail}.`
          ),
          p('Putem divulga date și când legea ne obligă (de exemplu, la solicitarea unui organ de anchetă).')
        ]
      },
      {
        id: 'pastrare',
        heading: '6. Cât timp păstrăm datele',
        blocks: [
          table(
            ['Date', 'Durată'],
            [
              ['Fotografii încărcate și imagini generate', SITE.retention.tempImages],
              ['Metadate job', SITE.retention.jobMetadata],
              ['Date de cont', SITE.retention.account],
              ['Coada de emailuri tranzacționale', SITE.retention.emailQueue],
              ['Documente fiscale (facturi, plăți)', SITE.retention.invoices],
              ['Jurnale de securitate', SITE.retention.securityLogs]
            ]
          ),
          p('Dacă îți exerciți dreptul de ștergere, eliminăm datele în cel mult 30 de zile, cu excepția celor pe care legea ne obligă să le păstrăm.')
        ]
      },
      {
        id: 'drepturi',
        heading: '7. Drepturile tale',
        blocks: [
          p('Ai următoarele drepturi, pe care le poți exercita printr-o cerere trimisă la ' + SITE.privacyEmail + ':'),
          ul([
            'acces — să afli dacă prelucrăm date despre tine și să primești o copie;',
            'rectificare — să corectăm datele inexacte;',
            'ștergere („dreptul de a fi uitat”) — în condițiile art. 17 GDPR;',
            'restricționare a prelucrării — art. 18 GDPR;',
            'portabilitate — să primești datele într-un format structurat, utilizat în mod curent;',
            'opoziție — împotriva prelucrărilor bazate pe interes legitim;',
            'retragerea consimțământului, oricând, fără a afecta legalitatea prelucrării anterioare;',
            'de a nu fi supus unei decizii automatizate cu efecte juridice.'
          ]),
          p(
            `Dacă apreciezi că prelucrarea încalcă legea, poți depune o plângere la ${SITE.dpa.name}, ` +
              `${SITE.dpa.address} — [${SITE.dpa.url}](${SITE.dpa.url}).`
          ),
          p(
            'Dreptul de acces și dreptul de ștergere le poți exercita direct din pagina „Cont” a ' +
              'aplicației: butonul „Descarcă datele mele” produce un fișier JSON cu toate datele ' +
              'păstrate, iar „Șterge contul” anonimizează contul imediat (rămân doar documentele ' +
              'fiscale, fără date de profil).' +
              ` Pentru orice altă cerere scrie la ${SITE.privacyEmail}.`
          )
        ]
      },
      {
        id: 'minori',
        heading: '8. Minori',
        blocks: [
          p(
            `Serviciul se adresează persoanelor de minimum ${SITE.minAge} ani. Sub această vârstă, ` +
              'folosirea contului necesită acordul reprezentantului legal. Dacă aflăm că am colectat date ' +
              'ale unui minor fără acordul necesar, ștergem contul și datele aferente.'
          )
        ]
      },
      {
        id: 'securitate',
        heading: '9. Securitate',
        blocks: [
          p(
            'Datele sunt transmise criptat (HTTPS), parolele sunt stocate doar ca hash BCrypt, accesul la ' +
              'datele de producție este limitat la operatori, iar cererile sunt limitate pentru a preveni ' +
              'abuzul. În caz de incident de securitate care îți afectează drepturile, îți notificăm și ' +
              'informăm autoritatea de supraveghere în 72 de ore, conform art. 33–34 GDPR.'
          )
        ]
      },
      {
        id: 'modificari',
        heading: '10. Modificări',
        blocks: [
          p(
            `Versiunea curentă: ${SITE.docVersionLabel}. Când modificăm politica în mod semnificativ, ` +
              'afișăm un anunț în aplicație și, dacă este necesar, cerem din nou consimțământul.'
          )
        ]
      }
    ]
  },

  // -------------------------------------------------------------------------
  cookies: {
    slug: 'cookies',
    title: 'Politica de cookie-uri',
    intro:
      'Această pagină explică ce cookie-uri și ce stocare locală folosim, în ce scop și cum îți ' +
      'poți schimba opțiunea oricând. Se aplică împreună cu Politica de confidențialitate.',
    sections: [
      {
        id: 'ce-sunt',
        heading: '1. Ce sunt cookie-urile',
        blocks: [
          p(
            'Cookie-urile sunt fișiere mici salvate de browser. Alături de ele folosim `localStorage`, ' +
              'care funcționează similar, dar nu este trimis automat la server. Le împărțim în două ' +
              'categorii: strict necesare (fără ele site-ul nu funcționează) și opționale ' +
              '(statistici, publicitate), care se activează doar cu acordul tău.'
          )
        ]
      },
      {
        id: 'consimtamant',
        heading: '2. Cum cerem acordul',
        blocks: [
          p(
            'La prima vizită apare un banner cu trei opțiuni: „Accept toate”, „Doar esențiale” și ' +
              '„Personalizează”. Refuzul este la fel de simplu ca acceptul — niciun script de ' +
              'statistică sau publicitate nu se încarcă înainte de acordul tău, iar consimțământul ' +
              'este salvat cu data, versiunea documentului și categoriile alese.'
          ),
          p(
            'Consimțământul este valabil maximum 6 luni, după care îl cerem din nou. Îl poți retrage ' +
              'oricând din linkul „Setări cookie-uri” din subsolul fiecărei pagini.'
          ),
          p(
            'Modul de consimțământ Google (Consent Mode v2) pornește implicit pe „denied”: până la ' +
              'acceptare, Google nu primește semnale pentru publicitate personalizată și nici cookie-uri ' +
              'de măsurare persistente.'
          )
        ]
      },
      {
        id: 'necesare',
        heading: '3. Strict necesare (nu pot fi dezactivate)',
        blocks: [
          table(
            ['Nume', 'Tip', 'Scop', 'Durată'],
            [
              ['`toonmint.token`', 'localStorage', 'păstrează sesiunea ta autentificată (JWT)', '24 de ore'],
              ['`toonmint.consent.v1`', 'localStorage', 'reține opțiunile tale de cookie-uri', '6 luni'],
              ['cookie-uri de sesiune ale găzduirii', 'cookie', 'rutare către server, echilibrare, protecție anti-abuz', 'sesiune']
            ]
          )
        ]
      },
      {
        id: 'optionale',
        heading: '4. Opționale (se activează doar cu acord)',
        blocks: [
          table(
            ['Furnizor', 'Cookie / stocare', 'Scop', 'Durată'],
            [
              ['Google Analytics 4', '`_ga`, `_ga_*`, `_gid`', 'statistici agregate despre vizite și pagini', 'până la 2 ani'],
              ['Google Ads / AdSense', '`_gcl_au`, `__gads`, `__gpi`, `IDE`, `test_cookie`', 'publicitate și măsurarea ei', 'până la 2 ani'],
              ['Microsoft Clarity', '`_clck`, `_clsk`, `CLID`, `MUID`', 'hărți de căldură și înregistrări anonimizate de sesiune', 'până la 1 an'],
              ['Google Tag Manager', '`_dc_gtm_*`', 'încarcă etichetele aprobate mai sus', 'sesiune']
            ]
          ),
          p('Dacă alegi „Doar esențiale”, niciunul dintre aceste scripturi nu este încărcat, iar cererile către furnizorii de mai sus nu sunt trimise.')
        ]
      },
      {
        id: 'gestionare',
        heading: '5. Cum îți schimbi opțiunea',
        blocks: [
          p('Poți modifica sau retrage acordul oricând: din linkul „Setări cookie-uri” din subsolul paginii. Retragerea are efect imediat pentru scripturile care nu au fost încă încărcate; pentru cele deja active, reîncărcarea paginii șterge cookie-urile lor.'),
          p(
            'Poți bloca cookie-uri și din setările browserului. Ghiduri oficiale: ' +
              '[Chrome](https://support.google.com/chrome/answer/95647), ' +
              '[Safari](https://support.apple.com/ro-ro/guide/safari/sfri11471/mac), ' +
              '[Firefox](https://support.mozilla.org/ro/kb/cookies-informatii-care-sunt-stocate-pe-computer).'
          ),
          p(
            'Pentru publicitate poți folosi și ' +
              '[setările Google Ads](https://adssettings.google.com) sau ' +
              '[opt-out-ul rețelelor publicitare](https://www.youronlinechoices.com/ro/).'
          )
        ]
      },
      {
        id: 'modificari',
        heading: '6. Modificări',
        blocks: [
          p(`Versiunea curentă: ${SITE.docVersionLabel}. Orice cookie nou apare în tabelele de mai sus înainte de a fi folosit.`)
        ]
      }
    ]
  },

  // -------------------------------------------------------------------------
  termeni: {
    slug: 'termeni',
    title: 'Termeni și condiții',
    intro:
      'Acești termeni sunt contractul dintre tine și furnizorul serviciului ToonMint. Prin crearea ' +
      'unui cont sau prin folosirea generării gratuite, confirmi că i-ai citit și că îi accepți.',
    sections: [
      {
        id: 'furnizor',
        heading: '1. Cine furnizează serviciul',
        blocks: [
          identity(),
          p(
            `Ne poți contacta pentru reclamații la ${ENTITY.email} sau ${ENTITY.phone}. Răspundem în ` +
              'cel mult 15 zile lucrătoare.'
          )
        ]
      },
      {
        id: 'serviciu',
        heading: '2. Ce este serviciul',
        blocks: [
          p(
            'ToonMint transformă o fotografie într-o imagine în stil cartoon/anime folosind modele de ' +
              'inteligență artificială. Serviciul include: generarea gratuită de probă (o singură dată ' +
              'per adresă de email, fără cont), contul cu email confirmat, creditele și abonamentele.'
          ),
          p('Serviciul este oferit „ca atare”: rezultatul generat de AI poate fi imperfect, poate diferi de stilul afișat în exemple și poate necesita mai multe încercări.')
        ]
      },
      {
        id: 'cont',
        heading: '3. Contul tău',
        blocks: [
          ul([
            'ai nevoie de o adresă de email validă, confirmată cu codul primit;',
            `trebuie să ai minimum ${SITE.minAge} ani;`,
            'ești responsabil pentru păstrarea parolei și pentru activitatea din cont;',
            'un cont este personal; revânzarea accesului sau partajarea credetelor nu este permisă;',
            'ne poți cere oricând ștergerea contului.'
          ])
        ]
      },
      {
        id: 'credite',
        heading: '4. Credite și abonamente',
        blocks: [
          p(
            'Creditele sunt o unitate internă de consum: o generare consumă creditele afișate în ' +
              'interfață. Creditele nu sunt bani, nu pot fi convertite în numerar și nu pot fi ' +
              'transferate altui cont.'
          ),
          ul([
            'abonamentele se reînnoiesc automat, dacă nu sunt anulate înainte de finalul perioadei;',
            'anularea se face din magazinul de aplicații sau din linkul de administrare a abonamentului primit de la procesator;',
            'creditele incluse într-un abonament se resetează la fiecare perioadă de facturare;',
            'creditele cumpărate separat rămân valabile 12 luni de la achiziție, dacă nu se specifică altfel la momentul cumpărării.'
          ])
        ]
      },
      {
        id: 'plati',
        heading: '5. Prețuri și plăți',
        blocks: [
          p(
            'Prețurile afișate includ TVA, dacă este aplicabil. Plata se procesează prin RevenueCat și ' +
              'prin magazinele de aplicații (App Store, Google Play) sau prin procesatorul de plăți ' +
              'integrat. Nu avem acces la datele cardului tău.'
          ),
          p('Factura se emite pe datele furnizate la plată și se transmite pe email conform legislației fiscale.')
        ]
      },
      {
        id: 'retragere',
        heading: '6. Dreptul de retragere (consumatori)',
        blocks: [
          p(
            'Conform OUG 34/2014, ai dreptul să te retragi din contract în 14 zile de la încheiere, ' +
              'fără a motiva decizia. Pentru conținutul digital livrat imediat (credite, generări), ' +
              'dreptul de retragere se pierde dacă:'
          ),
          ul([
            'ai solicitat expres începerea executării înainte de expirarea celor 14 zile;',
            'ai confirmat că ai luat cunoștință de faptul că îți pierzi dreptul de retragere.'
          ]),
          p(
            'Această confirmare este cerută la checkout, înainte de plată. Dacă te retragi înainte de ' +
              'consumarea creditelor, îți returnăm suma în maximum 14 zile, prin aceeași metodă de plată.'
          ),
          p(
            'Nu limităm niciun drept legal: produsul neconform, dublarea plății sau serviciul nelivrat ' +
              'se rezolvă prin rambursare sau prin reluarea generării.'
          )
        ]
      },
      {
        id: 'continut',
        heading: '7. Conținutul pe care îl încarci',
        blocks: [
          p('Rămâi proprietarul fotografiilor tale. Ne acorzi doar licența tehnică necesară pentru a procesa imaginea, a o transmite modelului AI și a-ți livra rezultatul. Nu folosim fotografiile tale în materiale de marketing fără un acord separat, explicit.'),
          p('Declari și garantezi că:'),
          ul([
            'ai drepturile necesare asupra imaginii (autor sau licență suficientă);',
            'ai acordul persoanelor care apar în fotografie;',
            'imaginea nu este a unui minor;',
            'nu încarci conținut ilegal, care încalcă drepturi de autor, dreptul la imagine, demnitatea sau siguranța unei persoane, nuditate, violență sau materiale de abuz asupra minorilor.'
          ]),
          p('Putem suspenda contul și refuza generarea dacă încălcarea este evidentă.')
        ]
      },
      {
        id: 'rezultate',
        heading: '8. Rezultatele generate',
        blocks: [
          p(
            'Poți folosi imaginea generată în scopuri personale sau comerciale, cu condiția să respecți ' +
              'legea. Îți punem la dispoziție rezultatul așa cum este; nu garantăm un anumit grad de ' +
              'asemănare, un anumit stil sau un anumit termen de generare.'
          ),
          p(
            'Rezultatele sunt create cu AI. Le marcăm ca atare, iar tu ești responsabil să nu le ' +
              'prezinți drept fotografii reale în contexte în care acest lucru ar induce în eroare ' +
              '(art. 50 AI Act). Nu folosi rezultatele pentru a crea imagini înșelătoare cu persoane ' +
              'reale, pentru a acredita afirmații false sau pentru hărțuire.'
          )
        ]
      },
      {
        id: 'interzis',
        heading: '9. Utilizare interzisă',
        blocks: [
          ul([
            'automatizarea accesului (boți, scraping) sau ocolirea limitelor de cereri și de credite;',
            'încercarea de a extrage codul, prompturile sau modelele;',
            'încărcarea de fișiere malware sau exploatarea unor vulnerabilități;',
            'crearea de conținut sexual, de ură, de hărțuire sau care exploatează minori.',
          ]),
          p('Încălcarea acestor reguli poate duce la suspendarea contului, fără a afecta dreptul tău la rambursare pentru serviciul neprestat.')
        ]
      },
      {
        id: 'disponibilitate',
        heading: '10. Disponibilitate și modificări',
        blocks: [
          p(
            'Serviciul poate fi indisponibil temporar pentru mentenanță sau din cauza unor furnizori ' +
              'externi (model AI, procesator de plăți, găzduire). Dacă o generare eșuează din vina ' +
              'noastră, creditele consumate se returnează automat în cont.'
          ),
          p('Putem modifica acești termeni; pentru schimbări semnificative anunțăm cu 15 zile înainte, iar continuarea folosirii înseamnă acceptare.')
        ]
      },
      {
        id: 'raspundere',
        heading: '11. Răspundere',
        blocks: [
          p(
            'Răspundem pentru obligațiile asumate în limitele legii. Nu excludem și nu limităm ' +
              'răspunderea pentru vătămare corporală, deces, dol sau culpă gravă și nici drepturile ' +
              'consumatorului prevăzute de lege. În restul cazurilor, răspunderea noastră este limitată ' +
              'la suma plătită de tine în ultimele 12 luni.'
          )
        ]
      },
      {
        id: 'litigii',
        heading: '12. Rezolvarea litigiilor',
        blocks: [
          p(
            `Încearcă mai întâi o soluționare amiabilă, scriindu-ne la ${ENTITY.email}. ` +
              `Poți apela și la ANPC — [SAL](${SITE.anpc.sal}) — sau la platforma europeană de ` +
              `soluționare online a litigiilor — [SOL](${SITE.anpc.sol}).`
          ),
          p('Contractul este guvernat de legea română. Consumatorii pot alege instanța de la domiciliul lor.')
        ]
      },
      {
        id: 'licente',
        heading: '13. Licențe și credite',
        blocks: [
          p(
            'Componentele open-source folosite și licențele lor sunt listate în ' +
              '[pagina de licențe](/legal/licente). Imaginile de exemplu provin din surse libere sau ' +
              'sunt generate cu AI în cadrul proiectului.'
          )
        ]
      }
    ]
  },

  // -------------------------------------------------------------------------
  licente: {
    slug: 'licente',
    title: 'Licențe și credite',
    intro:
      'Ce este proprietar, ce este open-source și de unde provin resursele folosite în ToonMint.',
    sections: [
      {
        id: 'continut',
        heading: '1. Conținutul site-ului',
        blocks: [
          p(
            `Textele, interfața, ilustrațiile și numele ${SITE.product} sunt protejate prin drept de ` +
              'autor și aparțin furnizorului sau sunt folosite cu licență. Nu pot fi copiate pentru ' +
              'a construi un serviciu similar fără acord scris.'
          ),
          p('Imaginile pe care le încarci rămân ale tale; drepturile asupra rezultatului generat sunt descrise în termeni (secțiunea 8).')
        ]
      },
      {
        id: 'open-source',
        heading: '2. Componente open-source',
        blocks: [
          p('Aplicația folosește biblioteci open-source, fiecare sub licența ei:'),
          table(
            ['Componentă', 'Licență'],
            [
              ['Spring Boot, Spring Security, Spring Data JPA (backend)', 'Apache License 2.0'],
              ['PostgreSQL JDBC driver, Flyway', 'BSD / Apache 2.0'],
              ['Vue 3, Vue Router, Pinia (frontend)', 'MIT'],
              ['Vite', 'MIT'],
              ['Axios', 'MIT'],
              ['swagger-ui / springdoc-openapi', 'Apache 2.0']
            ]
          ),
          p(
            'Lista completă, cu versiuni și textele licențelor, se generează cu ' +
              '`mvn license:aggregate-add-third-party` (backend) și `npx license-checker` (frontend). ' +
              'Textele integrale sunt disponibile la cerere.'
          )
        ]
      },
      {
        id: 'resurse',
        heading: '3. Resurse media',
        blocks: [
          p(
            'Fotografia de demonstrație „înainte” provine de pe Wikimedia Commons (CC0 — domeniu public). ' +
              'Imaginile „după” și previzualizările de stil au fost generate cu un model AI în cadrul ' +
              'proiectului. Detalii pe fișier: `frontend/public/demo/CREDITS.md`.'
          ),
          p(
            'Fonturile sunt încărcate de pe dispozitivul tău (fonturi de sistem sau `Inter` găzduit local). ' +
              'Nu facem cereri către Google Fonts, ca să nu transmitem adresa ta IP unui terț fără ' +
              'necesitate. Dacă vei adăuga un font extern, trebuie menționat aici și în politica de cookie-uri.'
          )
        ]
      },
      {
        id: 'marca',
        heading: '4. Marcă și nume',
        blocks: [
          p(
            `Numele și sigla ${SITE.product} sunt mărci ale furnizorului. Numele stilurilor din galerie ` +
              'sunt descriptive (anime, manga, pop art etc.) și nu implică vreo afiliere cu artiști, ' +
              'studiouri sau deținători de mărci.'
          )
        ]
      }
    ]
  }
}

// Ordinea de afișare în meniu / generarea rutelor.
export const DOCUMENT_SLUGS = Object.keys(DOCUMENTS)

/**
 * Documentul cerut, în limba potrivită: română pentru `ro`, engleză pentru
 * orice altă limbă (traducerile în celelalte 18 limbi ar cere un jurist).
 * Versiunea română prevalează în caz de neconcordanță — vezi `legal.notice`.
 */
export function getDocument(slug, locale) {
  const set = locale === 'ro' ? DOCUMENTS : DOCUMENTS_EN
  return set[slug] || set.confidentialitate
}
