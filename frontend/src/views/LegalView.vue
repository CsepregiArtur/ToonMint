<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { getDocument } from '../legal/documents'
import { LEGAL_LINKS, SITE } from '../legal/site'
import { formatDate, locale, t } from '../i18n'

// Paginile legale: /legal/confidentialitate, /legal/cookies, /legal/termeni, /legal/licente.
// Un singur component randează documentul cerut de rută, ca să nu duplicăm stilurile.
const route = useRoute()

const doc = computed(() => getDocument(route.params.doc, locale.value))

// Textul blocurilor suportă linkuri `[etichetă](url)` și fragmente de cod `...`.
// Le transformăm în segmente simple, randate fără `v-html` (fără risc de injectare).
const TOKEN = /(\[[^\]]+\]\([^)]+\)|`[^`]+`)/g

function segments(text) {
  return String(text)
    .split(TOKEN)
    .filter((part) => part !== '')
    .map((part) => {
      const link = part.match(/^\[([^\]]+)\]\(([^)]+)\)$/)
      if (link) {
        const href = link[2]
        const external = /^https?:/i.test(href)
        return { kind: 'link', text: link[1], href, external }
      }
      if (part.startsWith('`') && part.endsWith('`') && part.length > 1) {
        return { kind: 'code', text: part.slice(1, -1) }
      }
      return { kind: 'text', text: part }
    })
}
</script>

<template>
  <article class="legal">
    <nav class="legal-nav">
      <router-link
        v-for="link in LEGAL_LINKS"
        :key="link.to"
        :to="link.to"
        class="legal-tab"
        :class="{ active: link.to === `/legal/${doc.slug}` }"
      >
        {{ t(link.key) }}
      </router-link>
    </nav>

    <header class="legal-head">
      <h1>{{ doc.title }}</h1>
      <p class="muted small">
        {{ t('legal.version', { version: formatDate(SITE.docVersion), product: SITE.product, url: SITE.url }) }}
      </p>
      <p v-if="locale !== 'ro'" class="legal-lang-notice">🌐 {{ t('legal.notice') }}</p>
      <p class="legal-intro">{{ doc.intro }}</p>
    </header>

    <section v-for="section in doc.sections" :id="section.id" :key="section.id" class="legal-section">
      <h2>{{ section.heading }}</h2>

      <template v-for="(block, index) in section.blocks" :key="index">
        <p v-if="block.type === 'p'" class="legal-p">
          <template v-for="(seg, i) in segments(block.text)" :key="i">
            <a v-if="seg.kind === 'link'" :href="seg.href" :target="seg.external ? '_blank' : undefined"
               :rel="seg.external ? 'noopener noreferrer' : undefined">{{ seg.text }}</a>
            <code v-else-if="seg.kind === 'code'">{{ seg.text }}</code>
            <span v-else>{{ seg.text }}</span>
          </template>
        </p>

        <ul v-else-if="block.type === 'ul'" class="legal-list">
          <li v-for="(item, i) in block.items" :key="i">
            <template v-for="(seg, j) in segments(item)" :key="j">
              <a v-if="seg.kind === 'link'" :href="seg.href" :target="seg.external ? '_blank' : undefined"
                 :rel="seg.external ? 'noopener noreferrer' : undefined">{{ seg.text }}</a>
              <code v-else-if="seg.kind === 'code'">{{ seg.text }}</code>
              <span v-else>{{ seg.text }}</span>
            </template>
          </li>
        </ul>

        <div v-else-if="block.type === 'table'" class="legal-table-wrap">
          <table class="legal-table">
            <thead>
              <tr>
                <th v-for="(cell, i) in block.head" :key="i">{{ cell }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, r) in block.rows" :key="r">
                <td v-for="(cell, c) in row" :key="c">
                  <template v-for="(seg, k) in segments(cell)" :key="k">
                    <a v-if="seg.kind === 'link'" :href="seg.href" :target="seg.external ? '_blank' : undefined"
                       :rel="seg.external ? 'noopener noreferrer' : undefined">{{ seg.text }}</a>
                    <code v-else-if="seg.kind === 'code'">{{ seg.text }}</code>
                    <span v-else>{{ seg.text }}</span>
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <p v-else-if="block.type === 'note'" class="legal-note">
          <template v-for="(seg, i) in segments(block.text)" :key="i">
            <a v-if="seg.kind === 'link'" :href="seg.href" :target="seg.external ? '_blank' : undefined"
               :rel="seg.external ? 'noopener noreferrer' : undefined">{{ seg.text }}</a>
            <code v-else-if="seg.kind === 'code'">{{ seg.text }}</code>
            <span v-else>{{ seg.text }}</span>
          </template>
        </p>
      </template>
    </section>

    <footer class="legal-foot">
      <p class="muted small">
        {{ t('legal.operator') }}: {{ SITE.entity.name }} · {{ SITE.entity.address }} ·
        <a :href="`mailto:${SITE.entity.email}`">{{ SITE.entity.email }}</a>
      </p>
      <router-link class="btn ghost small legal-back" to="/">
        <img src="/toonmint-mark.svg" alt="" />{{ t('legal.back') }}
      </router-link>
    </footer>
  </article>
</template>

<style scoped>
.legal {
  display: flex;
  flex-direction: column;
  gap: 28px;
  max-width: 82ch;
  margin: 0 auto;
}

.legal-back img {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  vertical-align: middle;
  margin-right: 7px;
}

.legal-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.legal-tab {
  padding: 8px 14px;
  border: 1px solid var(--border);
  border-radius: 999px;
  color: var(--muted);
  font-size: 0.9rem;
}

.legal-tab.active {
  color: var(--text);
  background: rgba(255, 255, 255, 0.07);
}

.legal-head h1 {
  margin-bottom: 8px;
}

.legal-intro {
  margin-top: 14px;
  color: var(--muted);
}

.legal-lang-notice {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 10px;
  background: rgba(255, 179, 71, 0.12);
  color: #8a5a12;
  font-size: 0.85rem;
}

.legal-section h2 {
  margin-bottom: 12px;
  color: var(--primary);
  font-size: 1.1rem;
}

.legal-p,
.legal-list,
.legal-note {
  margin-top: 12px;
  color: var(--text);
}

.legal-p,
.legal-list li {
  line-height: 1.65;
}

.legal-list {
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.legal-note {
  padding: 14px 16px;
  border-left: 3px solid var(--primary);
  border-radius: 10px;
  background: rgba(255, 179, 71, 0.08);
  font-size: 0.92rem;
}

.legal-p code,
.legal-list code,
.legal-table code,
.legal-note code {
  padding: 1px 6px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.08);
  font-size: 0.85em;
}

.legal-table-wrap {
  margin-top: 14px;
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: 12px;
}

.legal-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.legal-table th,
.legal-table td {
  padding: 10px 12px;
  text-align: left;
  vertical-align: top;
  border-bottom: 1px solid var(--border);
}

.legal-table th {
  background: rgba(255, 255, 255, 0.04);
  color: var(--muted);
  font-weight: 600;
  white-space: nowrap;
}

.legal-table tr:last-child td {
  border-bottom: none;
}

.legal-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
</style>
