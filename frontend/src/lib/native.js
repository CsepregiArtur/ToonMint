import { Capacitor } from '@capacitor/core'

// Puntea către funcțiile care există doar în aplicațiile iOS/Android: camera și
// cumpărările prin App Store / Google Play. Pe web fiecare funcție este inactivă,
// iar fluxul rămâne cel existent (link RevenueCat + câmp de fișier).

// Indică dacă rulăm în interiorul aplicației native, nu în browser.
export function isNative() {
  return Capacitor.isNativePlatform()
}

// Platforma nativă curentă: `ios`, `android` sau `web`.
export function nativePlatform() {
  return Capacitor.getPlatform()
}

// Cheia publică RevenueCat a platformei (nu este secret: ajunge în bundle oricum).
function purchasesApiKey() {
  return nativePlatform() === 'ios'
    ? import.meta.env.VITE_REVENUECAT_IOS_API_KEY
    : import.meta.env.VITE_REVENUECAT_ANDROID_API_KEY
}

// SDK-ul se încarcă la cerere, ca bundle-ul web să nu îl tragă fără motiv.
async function purchasesSdk() {
  const { Purchases } = await import('@revenuecat/purchases-capacitor')
  return Purchases
}

let configuredUserId = ''

// Configurează SDK-ul o singură dată pentru utilizatorul curent. `appUserID` este
// identificatorul public al contului, exact cel folosit și de linkurile web, ca
// webhook-ul să găsească același utilizator.
export async function configurePurchases(appUserId) {
  if (!isNative() || !appUserId) {
    return false
  }
  const apiKey = purchasesApiKey()
  if (!apiKey) {
    throw new Error('RevenueCat API key is not configured for this platform')
  }
  if (configuredUserId === appUserId) {
    return true
  }
  const Purchases = await purchasesSdk()
  await Purchases.configure({ apiKey, appUserID: appUserId })
  configuredUserId = appUserId
  return true
}

// Verifică abonamentul activ din entitlement-ul RevenueCat (implicit `pro`).
export async function hasEntitlement(entitlementId = 'pro') {
  if (!isNative()) {
    return false
  }
  const Purchases = await purchasesSdk()
  const { customerInfo } = await Purchases.getCustomerInfo()
  return Boolean(customerInfo?.entitlements?.active?.[entitlementId])
}

// Găsește pachetul din offering-ul curent pentru un produs configurat în backend.
async function findPackage(productId) {
  const Purchases = await purchasesSdk()
  const offerings = await Purchases.getOfferings()
  const packages = offerings?.current?.availablePackages ?? []
  return packages.find((candidate) => candidate.product?.identifier === productId) ?? null
}

// Marcaj pe eroare pentru ca UI-ul să reacționeze ca la răspunsul serverului.
function subscriptionRequired() {
  const error = new Error('Credit packs are available only with an active subscription')
  error.code = 'SUBSCRIPTION_REQUIRED'
  return error
}

// Utilizatorul a închis fereastra magazinului: nu este o eroare de afișat.
function isCancellation(error) {
  const code = String(error?.code ?? '')
  return code === '1' || code === 'PURCHASE_CANCELLED_ERROR' || /cancel/i.test(error?.message ?? '')
}

// Cumpără un produs prin magazinul platformei.
// `requireSubscription` reproduce regula din backend: pachetele de credite sunt
// doar un supliment pentru abonați, iar în IAP verificarea nu trece prin server.
export async function purchaseProduct(productId, { entitlementId = 'pro', requireSubscription = false } = {}) {
  const Purchases = await purchasesSdk()
  const target = await findPackage(productId)
  if (!target) {
    throw new Error(`Product ${productId} is not available in the current offering`)
  }
  if (requireSubscription && !(await hasEntitlement(entitlementId))) {
    throw subscriptionRequired()
  }
  try {
    const { customerInfo } = await Purchases.purchasePackage({ aPackage: target })
    return { cancelled: false, customerInfo }
  } catch (error) {
    if (isCancellation(error)) {
      return { cancelled: true, customerInfo: null }
    }
    throw error
  }
}

// Reia cumpărările anterioare (cerut de App Store pentru abonamente).
export async function restorePurchases() {
  const Purchases = await purchasesSdk()
  const { customerInfo } = await Purchases.restorePurchases()
  return customerInfo
}

// Convertește base64 în octeți, pentru a refolosi fluxul de upload existent.
function base64ToBytes(value) {
  const binary = window.atob(value)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index)
  }
  return bytes
}

// Deschide camera sau galeria și întoarce un `File`, ca `JobUploader` să nu știe
// de unde vine fotografia.
export async function takePhoto() {
  const { Camera, CameraResultType, CameraSource } = await import('@capacitor/camera')
  let photo
  try {
    photo = await Camera.getPhoto({
      resultType: CameraResultType.Base64,
      source: CameraSource.Prompt,
      quality: 90,
      width: 2048,
      correctOrientation: true
    })
  } catch (error) {
    // Utilizatorul a anulat selectorul: nu este o eroare.
    if (/cancel/i.test(error?.message ?? '')) {
      return null
    }
    throw error
  }
  if (!photo?.base64String) {
    return null
  }
  const format = photo.format === 'png' ? 'png' : photo.format === 'webp' ? 'webp' : 'jpeg'
  return new File([base64ToBytes(photo.base64String)], `photo.${format}`, { type: `image/${format}` })
}
