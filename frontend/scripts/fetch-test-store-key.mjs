// Găsește cheia publică a Test Store-ului din RevenueCat prin API v2, ca să nu
// fie nevoie de click-uri prin dashboard, și o poate scrie direct în
// `.env.native.local` (unde e ignorată de git).
//
// Două moduri:
//   1. ai deja cheia publică → o dai ca argument, fără niciun apel de API
//        node scripts/fetch-test-store-key.mjs test_abc123 --write
//   2. n-ai cheia publică → o citește din API cu o cheie secretă **v2**
//        REVENUECAT_SECRET_API_KEY=sk_... node scripts/fetch-test-store-key.mjs --write
//
// Cheia secretă se dă prin variabilă de mediu, nu ca argument, ca să nu rămână în
// istoricul shell-ului, și nu este scrisă nicăieri: scriptul doar citește.
//
//   --project <nume>  proiectul, când contul are mai multe (implicit: ToonMint)
//   --write           scrie cheia în .env.native.local și setează stub-ul pe false
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const apiBase = (process.env.RC_API_BASE || 'https://api.revenuecat.com/v2').replace(/\/+$/, '')
const secretKey = (process.env.REVENUECAT_SECRET_API_KEY || process.env.RC_API_KEY || '').trim()

const args = process.argv.slice(2)
const projectQuery = args.includes('--project') ? args[args.indexOf('--project') + 1] : 'ToonMint'
const writeEnv = args.includes('--write')
const envFile = path.resolve(fileURLToPath(new URL('..', import.meta.url)), '.env.native.local')
// Cheia publică SDK, dacă utilizatorul o are deja: nu are rost să întrebăm API-ul.
const givenKey = args.find((argument) => /^(test|appl|goog|amzn)_[A-Za-z0-9_-]+$/.test(argument))

// Prefixele publice nu sunt secrete: SDK-ul le primește în bundle. Dacă ajung în
// variabila pentru cheia secretă, API-ul răspunde 401 „Invalid API key”.
const PUBLIC_KEY_PREFIX = /^(test|appl|goog|amzn)_/

async function api(pathname) {
  const response = await fetch(`${apiBase}${pathname}`, {
    headers: { Authorization: `Bearer ${secretKey}`, Accept: 'application/json' }
  })
  if (!response.ok) {
    const body = (await response.text()).slice(0, 300)
    if (/legacy API key/i.test(body)) {
      throw new Error(
        'cheia este o cheie veche, de API v1, iar v1 nu are endpoint-uri pentru aplicații ' +
          'sau chei publice. Creează una nouă cu API Version 2: Project settings → API keys → ' +
          'New secret API key (cu permisiune de citire pe project_configuration).'
      )
    }
    if (response.status === 401) {
      throw new Error(
        'API-ul nu recunoaște cheia secretă (401 Invalid API key): verifică dacă ai ' +
          'copiat-o întreagă și dacă e generată cu API Version 2. Dacă ai deja cheia ' +
          'publică a Test Store-ului, nu mai e nevoie de asta: ' +
          'node scripts/fetch-test-store-key.mjs test_... --write'
      )
    }
    throw new Error(`${pathname} → HTTP ${response.status}: ${body}`)
  }
  return response.json()
}

// API-ul v2 paginează prin `next_page`, care e o cale relativă la gazdă.
async function listAll(pathname) {
  const items = []
  let next = pathname
  while (next) {
    const page = await api(next.replace(/^https?:\/\/[^/]+/, ''))
    items.push(...(page.items ?? []))
    next = page.next_page ?? null
  }
  return items
}

function pickProject(projects) {
  const query = projectQuery.toLowerCase()
  return (
    projects.find((project) => project.id === projectQuery) ??
    projects.find((project) => (project.name ?? '').toLowerCase() === query) ??
    projects.find((project) => (project.name ?? '').toLowerCase().includes(query)) ??
    null
  )
}

function setVar(text, name, value) {
  const pattern = new RegExp(`^${name}=.*$`, 'm')
  return pattern.test(text) ? text.replace(pattern, `${name}=${value}`) : `${text.trimEnd()}\n${name}=${value}\n`
}

// Scrie cheia în ambele variabile de platformă și scoate stub-ul, ca build-ul
// nativ să folosească SDK-ul real.
async function applyKey(key) {
  if (!writeEnv) {
    console.log(
      '\nPune cheia în ambele variabile din .env.native.local sau rulează din nou cu --write.'
    )
    return
  }
  const envText = await readFile(envFile, 'utf8')
  const updated = [
    ['VITE_REVENUECAT_IOS_API_KEY', key],
    ['VITE_REVENUECAT_ANDROID_API_KEY', key],
    ['VITE_NATIVE_PURCHASE_STUB', 'false']
  ].reduce((text, [name, value]) => setVar(text, name, value), envText)
  await writeFile(envFile, updated, 'utf8')
  console.log(`\nScris în ${path.relative(process.cwd(), envFile)}. Verifică apoi cu:`)
  console.log('  node scripts/check-native-env.mjs ios')
  console.log('Cheia merge doar în build-uri Debug: SDK-ul refuză să pornească într-un Release.')
}

// Cheia publică dată direct ca argument: nu mai e nevoie de nicio cheie secretă.
if (givenKey) {
  console.log(`Cheie publică: ${givenKey}`)
  await applyKey(givenKey)
  process.exit(0)
}

if (!secretKey) {
  console.error(
    'Lipsește cheia secretă RevenueCat. Rulează:\n' +
      '  REVENUECAT_SECRET_API_KEY=sk_... node scripts/fetch-test-store-key.mjs\n' +
      'Sau, dacă ai deja cheia publică a Test Store-ului:\n' +
      '  node scripts/fetch-test-store-key.mjs test_... --write'
  )
  process.exit(1)
}

if (PUBLIC_KEY_PREFIX.test(secretKey)) {
  console.error(
    'Cheia din REVENUECAT_SECRET_API_KEY e o cheie publică SDK (test_/appl_/goog_/amzn_), ' +
      'nu una secretă — API-ul răspunde 401. Dacă asta e cheia Test Store, dă-o direct:\n' +
      `  node scripts/fetch-test-store-key.mjs ${secretKey}${writeEnv ? ' --write' : ''}`
  )
  process.exit(1)
}

try {
  const projects = await listAll('/projects')
  const project = pickProject(projects)
  if (!project) {
    console.error(
      `Nu am găsit proiectul "${projectQuery}". Proiecte disponibile: ` +
        projects.map((candidate) => `${candidate.name} (${candidate.id})`).join(', ')
    )
    process.exit(1)
  }

  const apps = await listAll(`/projects/${project.id}/apps`)
  const testStore = apps.find((app) => app.type === 'test_store')
  if (!testStore) {
    console.error(
      `Proiectul ${project.name} nu are încă un Test Store. Creează-l din dashboard ` +
        '(Project settings → Apps and providers → Test configuration); API-ul v2 nu ' +
        `permite crearea lui. Aplicații existente: ` +
        (apps.map((app) => `${app.name} (${app.type})`).join(', ') || 'niciuna')
    )
    process.exit(1)
  }

  const keys = await listAll(`/projects/${project.id}/apps/${testStore.id}/public_api_keys`)
  const testKey = keys.find((entry) => String(entry.key).startsWith('test_'))
  if (!testKey) {
    console.error(
      `Test Store-ul „${testStore.name}” nu are cheie publică test_… . Chei găsite: ` +
        (keys.map((entry) => entry.key).join(', ') || 'niciuna')
    )
    process.exit(1)
  }

  console.log(`Proiect:    ${project.name} (${project.id})`)
  console.log(`Test Store: ${testStore.name} (${testStore.id})`)
  console.log(`Cheie:      ${testKey.key}`)

  await applyKey(testKey.key)
} catch (error) {
  console.error(`Eroare: ${error.message}`)
  process.exit(1)
}
