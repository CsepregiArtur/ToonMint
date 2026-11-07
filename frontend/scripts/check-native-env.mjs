import { loadEnv } from 'vite'

// Verifică, înainte de compilare, variabilele fără de care build-ul nativ nu
// funcționează: adresa API (în WebView `/api` nu ajunge la server) și cheia
// publică RevenueCat a platformei. Se citește exact ce vede Vite la build.
const platform = (process.argv[2] || '').toLowerCase()
const env = loadEnv('native', process.cwd(), 'VITE_')
const problems = []

const apiBaseUrl = (env.VITE_API_BASE_URL || '').trim()
if (!apiBaseUrl) {
  problems.push(
    'VITE_API_BASE_URL is missing: the native app must call the public API (see .env.native.example)'
  )
} else if (!/^https?:\/\//i.test(apiBaseUrl)) {
  problems.push(`VITE_API_BASE_URL must be an absolute URL, got "${apiBaseUrl}"`)
} else if (apiBaseUrl.endsWith('/')) {
  problems.push(`VITE_API_BASE_URL must not end with a slash, got "${apiBaseUrl}"`)
}

// Stub-ul înlocuiește SDK-ul, deci cheile nu sunt necesare; build-ul nu trebuie însă
// folosit în magazine (cumpărările sunt doar simulate).
const stubbed = String(env.VITE_NATIVE_PURCHASE_STUB || '').toLowerCase() === 'true'

const keys = {
  ios: (env.VITE_REVENUECAT_IOS_API_KEY || '').trim(),
  android: (env.VITE_REVENUECAT_ANDROID_API_KEY || '').trim()
}
const platforms = platform ? [platform] : ['ios', 'android']
if (!stubbed) {
  for (const name of platforms) {
    if (!(name in keys)) {
      problems.push(`Unknown platform "${name}": expected "ios" or "android"`)
    } else if (!keys[name]) {
      problems.push(
        `VITE_REVENUECAT_${name.toUpperCase()}_API_KEY is missing: in-app purchases cannot be tested without it`
      )
    }
  }
} else if (platform && !(platform in keys)) {
  problems.push(`Unknown platform "${platform}": expected "ios" or "android"`)
}

// Cheia de Test Store (`test_…`) merge cu SDK-ul, dar SDK-ul refuză să pornească
// într-un build Release, deci avertizăm la fiecare build care o folosește.
if (!stubbed && Object.values(keys).some((key) => key.startsWith('test_'))) {
  console.warn(
    'WARNING: RevenueCat Test Store key (test_…) is configured — purchases are simulated ' +
      'by the SDK and a Release build refuses to start with it. Use it in Debug builds only ' +
      'and switch to the platform keys before shipping.'
  )
}

if (problems.length) {
  console.error('Native build check failed:')
  for (const problem of problems) {
    console.error(`  - ${problem}`)
  }
  console.error(
    '\nCopy .env.native.example to .env.native.local and fill in the RevenueCat keys.'
  )
  process.exit(1)
}

if (stubbed) {
  console.warn(
    'WARNING: VITE_NATIVE_PURCHASE_STUB=true — purchases are simulated and grant no credits. ' +
      'Never ship this build; use it only to rehearse the flow in a simulator or emulator.'
  )
}
console.log(`Native build check passed for ${platform || 'ios, android'} (${apiBaseUrl})`)
