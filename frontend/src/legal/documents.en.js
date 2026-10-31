import { SITE } from './site'

// ---------------------------------------------------------------------------
// English version of the legal documents. Shown for every language except
// Romanian; the Romanian version (`documents.js`) prevails in case of conflict.
//
// Keep the section ids identical to the Romanian file so anchors and deep links
// work in both languages.
// ---------------------------------------------------------------------------

const p = (text) => ({ type: 'p', text })
const ul = (items) => ({ type: 'ul', items })
const table = (head, rows) => ({ type: 'table', head, rows })
const note = (text) => ({ type: 'note', text })

const ENTITY = SITE.entity

const identity = () => {
  const lines = [`Name: ${ENTITY.name}`]
  if (ENTITY.cui) lines.push(`Tax ID (CUI/CIF): ${ENTITY.cui}`)
  if (ENTITY.regCom) lines.push(`Trade Register no.: ${ENTITY.regCom}`)
  lines.push(`Registered address: ${ENTITY.address}`)
  lines.push(`Email: ${ENTITY.email}`)
  lines.push(`Phone: ${ENTITY.phone}`)
  return ul(lines)
}

export const DOCUMENTS_EN = {
  // -------------------------------------------------------------------------
  confidentialitate: {
    slug: 'confidentialitate',
    title: 'Privacy policy',
    intro:
      'This policy explains what personal data we process when you use ToonMint, why we ' +
      `process it, how long we keep it and what rights you have. It applies to the site and ` +
      `account at ${SITE.url}.`,
    sections: [
      {
        id: 'operator',
        heading: '1. Who the controller is',
        blocks: [
          p('The controller of your personal data is the provider of the ToonMint service:'),
          identity(),
          p(
            `For any question about your data write to ${SITE.privacyEmail}. ` +
              'We answer within 30 days of receiving your request.'
          )
        ]
      },
      {
        id: 'date',
        heading: '2. What data we process',
        blocks: [
          table(
            ['Category', 'Examples', 'Source'],
            [
              [
                'Account data',
                'email address, display name, password stored as a BCrypt hash, role (user/operator), registration and email-confirmation dates',
                'you, when creating an account'
              ],
              [
                'Authentication data',
                'IP address at registration/sign-in, request timestamps, 6-digit one-time codes (valid 15 minutes)',
                'generated automatically'
              ],
              [
                'Uploaded photos',
                'the image you send for transformation',
                'you, when generating'
              ],
              [
                'Generated images',
                'the toon result, kept in a short-lived cache for download',
                'generated automatically'
              ],
              [
                'Job metadata',
                'chosen style, status, duration, file size, credits used',
                'generated automatically'
              ],
              [
                'Billing data',
                'transaction and product identifiers received from the payment processor, credit history. We never receive or store your card number.',
                'payment processor (RevenueCat, App Store, Google Play)'
              ],
              [
                'Correspondence',
                'messages you send us by email or through the contact form',
                'you'
              ]
            ]
          ),
          p('We do not process special categories of data (Art. 9 GDPR) and we do not ask for more data than the service needs.')
        ]
      },
      {
        id: 'scopuri',
        heading: '3. Why we process it and on what legal basis',
        blocks: [
          table(
            ['Purpose', 'Legal basis', 'Details'],
            [
              [
                'Creating and managing the account, sign-in, email confirmation, password reset',
                'performance of a contract — Art. 6(1)(b) GDPR',
                'without this data we cannot deliver the service'
              ],
              [
                'Generating images and delivering the result',
                'performance of a contract — Art. 6(1)(b)',
                'the photo is sent to the AI model only to produce the requested result'
              ],
              [
                'Billing, credits, subscriptions, payment fraud prevention',
                'performance of a contract — Art. 6(1)(b); legal obligation — Art. 6(1)(c) for accounting records',
                'amounts and fiscal documents are kept for 10 years'
              ],
              [
                'Abuse prevention (rate limiting, one free generation per email, account security)',
                'legitimate interest — Art. 6(1)(f)',
                'interest in keeping the service available and fairly paid for'
              ],
              [
                'Traffic measurement and statistics (Google Analytics, Microsoft Clarity)',
                'consent — Art. 6(1)(a)',
                'activated only after you press “Accept all” in the banner'
              ],
              [
                'Advertising (Google AdSense), where enabled',
                'consent — Art. 6(1)(a)',
                'the advertising script is not loaded without consent'
              ],
              [
                'Transactional messages (verification code, confirmations)',
                'performance of a contract — Art. 6(1)(b)',
                'these are not marketing and cannot be turned off separately'
              ]
            ]
          ),
          p('Email marketing (if we introduce it) will rely on separate consent, with an unsubscribe link in every message.')
        ]
      },
      {
        id: 'fotografii',
        heading: '4. Your photos and AI processing',
        blocks: [
          p(
            'Photos are stored only temporarily: when the upload reaches the server the image goes ' +
              'into a short-lived cache, and after generation and download it is deleted automatically. ' +
              'Only job metadata (style, status, date) remains in the database, never the image itself.'
          ),
          p(
            'For generation, the image is sent to our AI model provider (Google — Gemini API). Under ' +
              'Google’s terms for the paid API, content sent through the API is not used to train the ' +
              'models. The transfer is encrypted.'
          ),
          note(
            'If you upload a photo that shows other people, you need their permission. ' +
              'Do not upload photos of minors, identity documents or images you do not own.'
          ),
          p(
            'Generated results are marked as AI-created, and both the interface and the download ' +
              'metadata indicate their artificial origin (Art. 50 of Regulation (EU) 2024/1689 — AI Act).'
          )
        ]
      },
      {
        id: 'destinatari',
        heading: '5. Who else can access the data',
        blocks: [
          p('We do not sell your data. We share it only with the processors below, strictly for the service you requested:'),
          table(
            ['Provider', 'Role', 'Data'],
            [
              ['Google (Gemini API)', 'image generation', 'the uploaded photo and the style prompt'],
              ['Google (Analytics, Clarity, AdSense)', 'statistics and advertising', 'browser identifiers and pages visited — only with consent'],
              ['RevenueCat and the app stores', 'payments and subscriptions', 'customer identifier, product, subscription status'],
              ['Google (Gmail SMTP)', 'sending transactional emails', 'email address and message content'],
              ['Hosting and database provider', 'application hosting, PostgreSQL, Redis', 'all of the above, as a processor']
            ]
          ),
          p(
            'Some providers are outside the European Economic Area (mainly in the USA). Transfers rely ' +
              'on the adequacy decision for the EU–US Data Privacy Framework and/or the Standard ' +
              'Contractual Clauses approved by the European Commission. You can ask for details about ' +
              `the safeguards by writing to ${SITE.privacyEmail}.`
          ),
          p('We may also disclose data where the law requires it (for example at the request of an investigating authority).')
        ]
      },
      {
        id: 'pastrare',
        heading: '6. How long we keep data',
        blocks: [
          table(
            ['Data', 'Retention'],
            [
              ['Uploaded photos and generated images', SITE.retention.tempImages],
              ['Job metadata', SITE.retention.jobMetadata],
              ['Account data', SITE.retention.account],
              ['Transactional email queue', SITE.retention.emailQueue],
              ['Fiscal documents (invoices, payments)', SITE.retention.invoices],
              ['Security logs', SITE.retention.securityLogs]
            ]
          ),
          p('If you exercise your right to erasure we delete the data within 30 days, except what the law requires us to keep.')
        ]
      },
      {
        id: 'drepturi',
        heading: '7. Your rights',
        blocks: [
          p('You have the following rights, which you can exercise by writing to ' + SITE.privacyEmail + ':'),
          ul([
            'access — to know whether we process data about you and to receive a copy;',
            'rectification — to have inaccurate data corrected;',
            'erasure (“right to be forgotten”) — under the conditions of Art. 17 GDPR;',
            'restriction of processing — Art. 18 GDPR;',
            'portability — to receive your data in a structured, commonly used format;',
            'objection — against processing based on legitimate interest;',
            'withdrawal of consent, at any time, without affecting the lawfulness of prior processing;',
            'not to be subject to an automated decision with legal effects.'
          ]),
          p(
            `If you believe the processing infringes the law, you may lodge a complaint with the ${SITE.dpa.name}, ` +
              `${SITE.dpa.address} — [${SITE.dpa.url}](${SITE.dpa.url}).`
          ),
          p(
            'You can exercise your right of access and your right to erasure directly from the ' +
              '“Account” page of the application: “Download my data” produces a JSON file with ' +
              'everything we keep, and “Delete account” anonymises the account immediately (only ' +
              'fiscal records remain, without profile data).' +
              ` For any other request write to ${SITE.privacyEmail}.`
          )
        ]
      },
      {
        id: 'minori',
        heading: '8. Minors',
        blocks: [
          p(
            `The service is intended for people aged ${SITE.minAge} and over. Below that age, using the ` +
              'account requires the consent of a legal representative. If we learn that we collected a ' +
              'minor’s data without the required consent, we delete the account and the related data.'
          )
        ]
      },
      {
        id: 'securitate',
        heading: '9. Security',
        blocks: [
          p(
            'Data is transmitted over HTTPS, passwords are stored only as BCrypt hashes, access to ' +
              'production data is limited to operators, and requests are rate-limited to prevent abuse. ' +
              'In the event of a security incident affecting your rights we notify you and inform the ' +
              'supervisory authority within 72 hours, as required by Art. 33–34 GDPR.'
          )
        ]
      },
      {
        id: 'modificari',
        heading: '10. Changes',
        blocks: [
          p(
            `Current version: ${SITE.docVersionLabelEn}. When we change this policy in a significant way we ` +
              'show a notice in the application and, where necessary, ask for consent again.'
          )
        ]
      }
    ]
  },

  // -------------------------------------------------------------------------
  cookies: {
    slug: 'cookies',
    title: 'Cookie policy',
    intro:
      'This page explains which cookies and which local storage we use, for what purpose and how you ' +
      'can change your choice at any time. It applies together with the Privacy policy.',
    sections: [
      {
        id: 'ce-sunt',
        heading: '1. What cookies are',
        blocks: [
          p(
            'Cookies are small files stored by your browser. Alongside them we use `localStorage`, ' +
              'which works similarly but is not sent to the server automatically. We split them into two ' +
              'groups: strictly necessary (the site cannot work without them) and optional (analytics, ' +
              'advertising), which are enabled only with your consent.'
          )
        ]
      },
      {
        id: 'consimtamant',
        heading: '2. How we ask for consent',
        blocks: [
          p(
            'On your first visit a banner offers three options: “Accept all”, “Essential only” and ' +
              '“Customise”. Refusing is as easy as accepting — no analytics or advertising script is ' +
              'loaded before your consent, and the consent is stored with the date, the document ' +
              'version and the categories you chose.'
          ),
          p(
            'Consent is valid for up to 6 months, after which we ask again. You can withdraw it at any ' +
              'time from the “Cookie settings” link in the footer of every page.'
          ),
          p(
            'Google Consent Mode v2 starts as “denied”: until you accept, Google receives no signal ' +
              'for personalised advertising and sets no persistent measurement cookies.'
          )
        ]
      },
      {
        id: 'necesare',
        heading: '3. Strictly necessary (cannot be disabled)',
        blocks: [
          table(
            ['Name', 'Type', 'Purpose', 'Duration'],
            [
              ['`toonmint.token`', 'localStorage', 'keeps your session signed in (JWT)', '24 hours'],
              ['`toonmint.consent.v1`', 'localStorage', 'remembers your cookie choices', '6 months'],
              ['`toonmint.locale`', 'localStorage', 'remembers your language', 'until you change it'],
              ['hosting session cookies', 'cookie', 'routing to the server, load balancing, anti-abuse', 'session']
            ]
          )
        ]
      },
      {
        id: 'optionale',
        heading: '4. Optional (enabled only with consent)',
        blocks: [
          table(
            ['Provider', 'Cookie / storage', 'Purpose', 'Duration'],
            [
              ['Google Analytics 4', '`_ga`, `_ga_*`, `_gid`', 'aggregated statistics about visits and pages', 'up to 2 years'],
              ['Google Ads / AdSense', '`_gcl_au`, `__gads`, `__gpi`, `IDE`, `test_cookie`', 'advertising and its measurement', 'up to 2 years'],
              ['Microsoft Clarity', '`_clck`, `_clsk`, `CLID`, `MUID`', 'heatmaps and anonymised session recordings', 'up to 1 year'],
              ['Google Tag Manager', '`_dc_gtm_*`', 'loads the tags approved above', 'session']
            ]
          ),
          p('If you choose “Essential only”, none of these scripts is loaded and no requests are sent to the providers above.')
        ]
      },
      {
        id: 'gestionare',
        heading: '5. How to change your choice',
        blocks: [
          p('You can change or withdraw your consent at any time from the “Cookie settings” link in the page footer. Withdrawal takes effect immediately for scripts not yet loaded; for those already running, reloading the page clears their cookies.'),
          p(
            'You can also block cookies in your browser settings. Official guides: ' +
              '[Chrome](https://support.google.com/chrome/answer/95647), ' +
              '[Safari](https://support.apple.com/en-gb/guide/safari/sfri11471/mac), ' +
              '[Firefox](https://support.mozilla.org/en-US/kb/cookies-information-websites-store-on-your-computer).'
          ),
          p(
            'For advertising you can also use ' +
              '[Google Ads settings](https://adssettings.google.com) or the ' +
              '[ad-network opt-out page](https://www.youronlinechoices.com/).'
          )
        ]
      },
      {
        id: 'modificari',
        heading: '6. Changes',
        blocks: [
          p(`Current version: ${SITE.docVersionLabelEn}. Any new cookie appears in the tables above before it is used.`)
        ]
      }
    ]
  },

  // -------------------------------------------------------------------------
  termeni: {
    slug: 'termeni',
    title: 'Terms and conditions',
    intro:
      'These terms are the contract between you and the provider of ToonMint. By creating an account ' +
      'or using the free generation you confirm that you have read and accept them.',
    sections: [
      {
        id: 'furnizor',
        heading: '1. Who provides the service',
        blocks: [
          identity(),
          p(
            `You can contact us with any complaint at ${ENTITY.email} or ${ENTITY.phone}. We reply within ` +
              '15 working days.'
          )
        ]
      },
      {
        id: 'serviciu',
        heading: '2. What the service is',
        blocks: [
          p(
            'ToonMint turns a photograph into an image in a cartoon/anime style using artificial ' +
              'intelligence models. The service includes: the free trial generation (once per email ' +
              'address, no account), the account with a confirmed email, credits and subscriptions.'
          ),
          p('The service is provided “as is”: an AI-generated result may be imperfect, may differ from the examples shown in the gallery and may need several attempts.')
        ]
      },
      {
        id: 'cont',
        heading: '3. Your account',
        blocks: [
          ul([
            'you need a valid email address, confirmed with the code you receive;',
            `you must be at least ${SITE.minAge} years old;`,
            'you are responsible for keeping your password safe and for activity in your account;',
            'an account is personal; reselling access or sharing credits is not allowed;',
            'you can ask us to delete your account at any time.'
          ])
        ]
      },
      {
        id: 'credite',
        heading: '4. Credits and subscriptions',
        blocks: [
          p(
            'Credits are an internal unit of consumption: one generation uses the credits shown in the ' +
              'interface. Credits are not money, cannot be exchanged for cash and cannot be transferred ' +
              'to another account.'
          ),
          ul([
            'subscriptions renew automatically unless cancelled before the end of the period;',
            'you can cancel from the app store or from the subscription-management link you receive from the processor;',
            'credits included in a subscription reset at each billing period;',
            'credits bought separately remain valid for 12 months from purchase unless stated otherwise at the time of purchase.'
          ])
        ]
      },
      {
        id: 'plati',
        heading: '5. Prices and payments',
        blocks: [
          p(
            'Displayed prices include VAT where applicable. Payments are processed by RevenueCat and by ' +
              'the app stores (App Store, Google Play) or the integrated payment processor. We never see ' +
              'your card details.'
          ),
          p('The invoice is issued with the details you provide at checkout and sent by email as required by tax law.')
        ]
      },
      {
        id: 'retragere',
        heading: '6. Right of withdrawal (consumers)',
        blocks: [
          p(
            'If you are a consumer in the EU you have the right to withdraw from the contract within 14 ' +
              'days of concluding it, without giving a reason. For digital content delivered ' +
              'immediately (credits, generations), the right of withdrawal is lost if:'
          ),
          ul([
            'you expressly asked for performance to begin before the 14 days had expired;',
            'you acknowledged that you therefore lose the right of withdrawal.'
          ]),
          p(
            'That acknowledgement is requested at checkout, before payment. If you withdraw before using ' +
              'the credits, we refund the amount within 14 days using the same payment method.'
          ),
          p(
            'We do not limit any statutory right: a non-conforming product, a duplicate payment or a ' +
              'service that was not delivered is resolved by refund or by regenerating the image.'
          )
        ]
      },
      {
        id: 'continut',
        heading: '7. Content you upload',
        blocks: [
          p('You remain the owner of your photographs. You grant us only the technical licence needed to process the image, send it to the AI model and deliver the result. We do not use your photos in marketing without separate, explicit consent.'),
          p('You declare and warrant that:'),
          ul([
            'you hold the necessary rights to the image (author or sufficient licence);',
            'you have the consent of the people shown in the photograph;',
            'the image does not show a minor;',
            'you do not upload illegal content, content that infringes copyright, image rights, dignity or anyone’s safety, nudity, violence or child sexual abuse material.'
          ]),
          p('We may suspend the account and refuse generation where the breach is obvious.')
        ]
      },
      {
        id: 'rezultate',
        heading: '8. Generated results',
        blocks: [
          p(
            'You may use the generated image for personal or commercial purposes, provided you comply ' +
              'with the law. We provide the result as it is; we do not guarantee a particular degree of ' +
              'likeness, a particular style or a particular generation time.'
          ),
          p(
            'Results are AI-created. We mark them as such, and you are responsible for not presenting ' +
              'them as real photographs in contexts where that would mislead (Art. 50 AI Act). Do not use ' +
              'results to create misleading images of real people, to support false claims or to harass ' +
              'anyone.'
          )
        ]
      },
      {
        id: 'interzis',
        heading: '9. Prohibited use',
        blocks: [
          ul([
            'automated access (bots, scraping) or circumventing rate limits and credits;',
            'attempting to extract the code, prompts or models;',
            'uploading malware or exploiting vulnerabilities;',
            'creating sexual content, hate content, harassment or content that exploits minors.'
          ]),
          p('Breaching these rules may lead to suspension of the account, without affecting your right to a refund for the service not provided.')
        ]
      },
      {
        id: 'disponibilitate',
        heading: '10. Availability and changes',
        blocks: [
          p(
            'The service may be temporarily unavailable for maintenance or because of external providers ' +
              '(AI model, payment processor, hosting). If a generation fails through our fault, the ' +
              'credits used are returned to your account automatically.'
          ),
          p('We may change these terms; for significant changes we give 15 days’ notice, and continued use means acceptance.')
        ]
      },
      {
        id: 'raspundere',
        heading: '11. Liability',
        blocks: [
          p(
            'We are liable for our obligations within the limits of the law. We do not exclude or limit ' +
              'liability for death or personal injury, wilful misconduct or gross negligence, nor any ' +
              'consumer right granted by law. In all other cases our liability is limited to the amount ' +
              'you paid in the last 12 months.'
          )
        ]
      },
      {
        id: 'litigii',
        heading: '12. Dispute resolution',
        blocks: [
          p(
            `Please try an amicable resolution first by writing to ${ENTITY.email}. ` +
              `You may also contact your national consumer authority or the European online dispute ` +
              `resolution platform — [ODR](${SITE.anpc.sol}).`
          ),
          p('This contract is governed by Romanian law. Consumers may choose the courts of their place of residence.')
        ]
      },
      {
        id: 'licente',
        heading: '13. Licences and credits',
        blocks: [
          p(
            'The open-source components used and their licences are listed on the ' +
              '[licences page](/legal/licente). Sample images come from free sources or were generated ' +
              'with AI within the project.'
          )
        ]
      }
    ]
  },

  // -------------------------------------------------------------------------
  licente: {
    slug: 'licente',
    title: 'Licences and credits',
    intro: 'What is proprietary, what is open source, and where the resources used in ToonMint come from.',
    sections: [
      {
        id: 'continut',
        heading: '1. Site content',
        blocks: [
          p(
            `The texts, the interface, the illustrations and the ${SITE.product} name are protected by ` +
              'copyright and belong to the provider or are used under licence. They may not be copied to ' +
              'build a similar service without written permission.'
          ),
          p('Images you upload remain yours; the rights in the generated result are described in the terms (section 8).')
        ]
      },
      {
        id: 'open-source',
        heading: '2. Open-source components',
        blocks: [
          p('The application uses open-source libraries, each under its own licence:'),
          table(
            ['Component', 'Licence'],
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
            'The complete list, with versions and licence texts, is generated with ' +
              '`mvn license:aggregate-add-third-party` (backend) and `npx license-checker` (frontend). ' +
              'Full texts are available on request.'
          )
        ]
      },
      {
        id: 'resurse',
        heading: '3. Media assets',
        blocks: [
          p(
            'The “before” demo photograph comes from Wikimedia Commons (CC0 — public domain). The ' +
              '“after” images and the style previews were generated with an AI model within the project. ' +
              'Details in `frontend/public/demo/CREDITS.md`.'
          ),
          p(
            'Fonts are loaded from your device (system fonts or locally hosted `Inter`). We make no ' +
              'requests to Google Fonts, so your IP address is not sent to a third party unnecessarily. ' +
              'If an external font is added, it must be listed here and in the cookie policy.'
          )
        ]
      },
      {
        id: 'marca',
        heading: '4. Trademark and names',
        blocks: [
          p(
            `The ${SITE.product} name and logo are trademarks of the provider. The style names in the ` +
              'gallery are descriptive (anime, manga, pop art, etc.) and imply no affiliation with any ' +
              'artist, studio or trademark owner.'
          )
        ]
      }
    ]
  }
}
