// Raport de acoperire a traducerilor.
//
//   cd frontend && node scripts/i18n-coverage.mjs
//
// Afișează, pentru fiecare limbă, ce procent din cheile englezești este tradus.
// O cheie lipsă nu rupe nimic: `t()` cade pe engleză, apoi pe română.
//
// Fișierele de limbă sunt citite direct din folder (import dinamic), ca scriptul
// să ruleze cu Node fără bundler — de aceea nu importă `src/i18n/index.js`.
import { readdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const dir = join(here, '..', 'src', 'i18n', 'locales')

const files = readdirSync(dir).filter((name) => name.endsWith('.js')).sort()

const dictionaries = {}
for (const file of files) {
  const code = file.replace(/\.js$/, '')
  const module = await import(pathToFileURL(join(dir, file)).href)
  dictionaries[code] = module.default
}

const base = Object.keys(dictionaries.en)
let incomplete = 0

for (const code of Object.keys(dictionaries)) {
  const dict = dictionaries[code]
  const missing = base.filter((key) => dict[key] == null)
  const extra = Object.keys(dict).filter((key) => !(key in dictionaries.en))

  if (missing.length === 0 && extra.length === 0) {
    console.log(`OK  ${code.padEnd(3)} complet (${base.length} chei)`)
    continue
  }

  incomplete += 1
  const percent = Math.round(((base.length - missing.length) / base.length) * 100)
  console.log(`--  ${code.padEnd(3)} ${String(percent).padStart(3)}% — lipsesc ${missing.length} chei`)
  if (extra.length) {
    console.log(`      chei în plus: ${extra.join(', ')}`)
  }
}

console.log(
  `\n${Object.keys(dictionaries).length - incomplete}/${Object.keys(dictionaries).length} limbi complete · ${base.length} chei în engleză.`
)
