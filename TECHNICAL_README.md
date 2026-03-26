![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![minSdk](https://img.shields.io/badge/Min%20SDK-26-4285F4?style=flat-square)
![targetSdk](https://img.shields.io/badge/Target%20SDK-35-4285F4?style=flat-square)
![MVVM](https://img.shields.io/badge/Architecture-MVVM-orange?style=flat-square)
![Hilt](https://img.shields.io/badge/DI-Hilt-FF6F00?style=flat-square)
![MIT](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

**[By Sandi Utomo](https://github.com/sandiutomo)** — [LinkedIn](https://www.linkedin.com/in/sandiutomo/)

---

# MarketApp — Android Analytics Sample (Technical Documentation)

A sample Android e-commerce app demonstrating patterns for wiring multiple analytics and martech SDKs through a single `AnalyticsManager` fan-out layer — with Firebase Gemini AI, session replay coordination, feature flagging across five platforms, and GDPR-aware consent management. **This is a learning project, not production-ready code.**

> Look at [README.md](README.md) for a quick start guide.
>
> **Note:** This is a living project. SDKs, versions, and features evolve. Check the code for current implementations.

---

## Table of Contents

1. [Architecture](#architecture)
2. [Tech Stack](#tech-stack)
3. [Analytics SDKs](#analytics-sdks)
4. [AnalyticsManager API](#analyticsmanager-api)
5. [Event Taxonomy](#event-taxonomy)
6. [Firebase AI (Gemini)](#firebase-ai-gemini)
7. [Feature Flagging](#feature-flagging)
8. [Session Replay](#session-replay)
9. [Deep Link Handling](#deep-link-handling)
10. [Consent Management](#consent-management)
11. [Debug Panel](#debug-panel)
12. [Server-Side GTM (sGTM)](#server-side-gtm-sgtm)
13. [Logcat Reference](#logcat-reference)

---

## Architecture

```
MarketApplication.onCreate()
        │
        ▼
AnalyticsManager.initialize()       ← once, async parallel on Dispatchers.IO
        │  SupervisorJob — one tracker failing never blocks the others
        ├─ FirebaseTracker           GA4 events + Crashlytics breadcrumbs
        ├─ AmplitudeTracker          Product analytics + Session Replay + Experiment
        ├─ MixpanelTracker           Funnels + People profiles + Session Replay
        ├─ PostHogTracker            Feature flags + Session Replay
        ├─ AppsFlyerTracker          Attribution + OneLink deep links
        ├─ BrazeTracker              CRM + push + in-app messages + content cards
        ├─ OneSignalTracker          Push + in-app + outcomes
        ├─ ClarityTracker            Heatmaps + session recordings
        ├─ FacebookTracker           Meta pixel + conversion API
        ├─ SegmentTracker            CDP pipeline
        └─ StatsigTracker            Feature gates + A/B experiments

UI / ViewModel
        │
        ├─ analyticsManager.track(AnalyticsEvent)    → fan-out to all 11 SDKs
        ├─ analyticsManager.identify(userId, props)  → fan-out to all 11 SDKs
        └─ analyticsManager.reset()                  → on sign-out
```

### Key Guarantees

- **Async-safe initialization:** All `track()` calls join `initJob` before dispatching — no events fire before init completes.
- **Deduplication:** 200 ms window prevents double-fire on rapid re-renders.
- **Fault isolation:** `SupervisorJob` ensures one SDK crashing or timing out does not block the others.
- **Platform-exclusive routing:** Some events target only one platform via `isBrazeOnly` / `isAmplitudeOnly` flags.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Architecture | MVVM + Jetpack Navigation |
| DI | Hilt |
| Async | Coroutines + Flow |
| Networking | Retrofit + OkHttp |
| Image loading | Coil |
| Min SDK | 26 (Android 8.0) |
| Target / Compile SDK | 35 |

---

## Analytics SDKs

![Firebase](https://img.shields.io/badge/Firebase-BOM%2033.14-FFCA28?style=flat-square&logo=firebase&logoColor=black)
![Amplitude](https://img.shields.io/badge/Amplitude-1.26-0078FF?style=flat-square)
![Mixpanel](https://img.shields.io/badge/Mixpanel-8.3-7856FF?style=flat-square)
![PostHog](https://img.shields.io/badge/PostHog-3.7-F54E00?style=flat-square&logo=posthog&logoColor=white)
![AppsFlyer](https://img.shields.io/badge/AppsFlyer-6.15-00B2A9?style=flat-square)
![Braze](https://img.shields.io/badge/Braze-33.1-FF6B6B?style=flat-square)
![OneSignal](https://img.shields.io/badge/OneSignal-5.6-E54B4D?style=flat-square)
![Clarity](https://img.shields.io/badge/Microsoft%20Clarity-3.8-00A4EF?style=flat-square&logo=microsoft&logoColor=white)
![Meta](https://img.shields.io/badge/Meta-18.0-0866FF?style=flat-square&logo=meta&logoColor=white)
![Segment](https://img.shields.io/badge/Segment-1.20-52BD95?style=flat-square)
![Statsig](https://img.shields.io/badge/Statsig-4.41-194BFB?style=flat-square)

| SDK | Version | Role |
|---|---|---|
| Firebase Analytics + Crashlytics + AI | BOM 33.14.0 | GA4 events, crash reporting, Gemini generative AI |
| Amplitude | 1.26.0 + SR 0.24.1 + Experiment 1.13.1 | Product analytics, LTV, session replay, A/B experiments |
| Mixpanel | 8.3.0 + SR 1.0.6 | Funnel analysis, people profiles, session replay |
| PostHog | 3.7.1 | Feature flags, session replay, product analytics |
| AppsFlyer | 6.15.2 | Mobile attribution, deep links, ROAS |
| Braze | 33.1.0 | CRM, push, in-app, content cards, geofences |
| OneSignal | 5.6.2 | Push, in-app messages, outcomes |
| Microsoft Clarity | 3.8.1 | Heatmaps, session recordings |
| Meta App Events | 18.0.3 | Meta pixel, conversion API |
| Segment | 1.20.0 | CDP data pipeline layer |
| Statsig | 4.41.0 | Feature gates, A/B experiments |

---

## AnalyticsManager API

```kotlin
analyticsManager.initialize()                        // call once in Application.onCreate()
analyticsManager.track(event: AnalyticsEvent)        // fan-out to all 11 SDKs
analyticsManager.identify(userId, properties)        // set persistent user attributes
analyticsManager.alias(newId, oldId)                 // anonymous → identified merge
analyticsManager.reset()                             // clear identity on sign-out
analyticsManager.onNewPushToken(token)               // broadcast FCM token to all SDKs
analyticsManager.maskView(view)                      // PII masking across all replay SDKs
analyticsManager.setAnalyticsConsent(enabled)        // GDPR opt-in / opt-out, all SDKs
analyticsManager.trackError(screen, code, message)   // shorthand for ErrorOccurred event
analyticsManager.captureLog(level, message, tag)     // PostHog debug log capture
analyticsManager.shutdown()                          // flush all pending events before exit
```

---

## Event Taxonomy

All events use GA4 standard names and parameter keys wherever a standard exists. Platform-specific behavior lives inside each tracker — the call site always uses `AnalyticsEvent`.

### App Lifecycle

| Event | Key properties |
|---|---|
| `app_open` | — |
| `app_background` | — |
| `screen_view` | `screen_name`, `screen_class` |

### Auth & Onboarding

| Event | Key properties |
|---|---|
| `onboarding_completed` | `method` |
| `login` | `method` (`email` / `google`) |
| `sign_up` | `method` |
| `user_signed_out` | `session_duration_ms` |

### Product Discovery

| Event | Key properties |
|---|---|
| `search` | `search_term`, `result_count` |
| `search_result_tapped` | `item_id`, `position` |
| `select_content` | `category`, `content_type="category"` |
| `view_item_list` | `item_list_id`, `item_list_name`, `items[]` |
| `select_item` | `item_list_id`, `item_list_name`, `item`, `index` |
| `view_promotion` | `promotion_id`, `promotion_name`, `creative_name?`, `creative_slot?`, `location_id?` |
| `select_promotion` | same as `view_promotion` |

### Product Detail

| Event | Key properties |
|---|---|
| `view_item` | `item_id`, `item_name`, `price`, `item_category`, `currency`, `value`, `source` |
| `product_image_swiped` | `item_id`, `image_index` |
| `share` | `item_id`, `method`, `content_type="product"` |
| `add_to_wishlist` | `item_id`, `item_name`, `price`, `value`, `currency` |
| `remove_from_wishlist` | same |

### Cart

| Event | Key properties |
|---|---|
| `add_to_cart` | `item_id`, `item_name`, `price`, `quantity`, `item_category`, `value`, `currency` |
| `remove_from_cart` | `item_id`, `item_name`, `price`, `quantity`, `value`, `currency` |
| `view_cart` | `value`, `currency`, `item_count`, `items[]` |

### Checkout Funnel

| Event | Key properties |
|---|---|
| `begin_checkout` | `value`, `currency`, `item_count`, `items[]` |
| `checkout_address_selected` | `address_id`, `city`, `country`, `is_new_address` |
| `add_shipping_info` | `value`, `currency`, `shipping_tier`, `coupon?`, `item_count`, `items[]` |
| `add_payment_info` | `payment_type`, `currency`, `value?`, `items?` |
| `purchase` | `transaction_id`, `value`, `currency`, `coupon?`, `payment_method`, `item_count`, `items[]` |
| `order_failed` | `reason` |
| `refund` | `transaction_id`, `value`, `currency`, `items?` |

### Campaign & Attribution

| Event | Key properties |
|---|---|
| `campaign_open` | `source`, `medium?`, `campaign?`, `term?`, `content?`, `deep_link?` |
| `notification_receive` | `campaign_id?`, `type` |
| `notification_open` | `campaign_id?`, `deep_link?` |
| `push_permission_result` | `granted` |

### User Profile

| Event | Key properties |
|---|---|
| `address_added` | — |
| `payment_method_added` | — |
| `subscribe` | — |
| `error_occurred` | `screen`, `code`, `message` |

### Debug Only

| Event | Trigger | Reaches |
|---|---|---|
| `trigger_for_pushnotif` | QA panel → Test Push | **Braze only** |
| `trigger_for_banner` | QA panel → Test Banner | **Braze only** |
| `trigger_for_inapp` | QA panel → Test In-App | **Braze only** |
| `trigger_for_content_card` | QA panel → Test Content Card | **Braze only** |
| `trigger_for_amplitude_guide` | QA panel → Amplitude Guide Trigger | **Amplitude only** |

---

## Data Models

### EcommerceItem

```kotlin
EcommerceItem(
    itemId       = "sku-123",
    itemName     = "Product Name",
    price        = 199000.0,
    quantity     = 1,
    category     = "Electronics",
    brand        = "BrandName",
    variant      = "Blue/XL",
    discount     = 10000.0,
    index        = 0,
    coupon       = "SAVE10",
    itemListId   = "home_feed",
    itemListName = "Home Feed"
)
```

### UserProperties

```kotlin
UserProperties(
    userId            = "user-uuid",
    email             = "user@example.com",
    name              = "Full Name",
    firstName         = "First",
    lastName          = "Last",
    phone             = "+62xxx",
    country           = "ID",
    currency          = "IDR",
    loginMethod       = "email",
    hasPurchased      = true,
    orderCount        = 3,
    lifetimeValue     = 750000.0,
    preferredCategory = "Electronics",
    deviceId          = "...",
    appSetId          = "...",
    advertisingId     = "...",
    customAttributes  = mapOf("tier" to "gold")
)
```

---

## Firebase AI (Gemini)

Powered by `firebase-ai` (BOM 33.14.0), backend: Google AI, model: `gemini-2.0-flash`. All features use `runCatching` — failures fall back silently.

| Feature | Remote Config flag | Trigger | Fallback |
|---|---|---|---|
| Order confirmation message | `order_ai_message_enabled` | `placeOrder()` | Empty string |
| Home feed ranking | `product_recommendations_enabled` | `HomeViewModel` on load (with order history) | Default order |
| Semantic search | `ai_search_enabled` | IME Search key, queries ≥ 4 chars | Keyword search |

### Search Architecture

**Typing (debounced 800 ms, ≥ 2 chars):** Keyword search only, zero API calls.

**IME Search key:** AI semantic search, one Gemini call per submit.

**Result cache:** `AiRepository` caches up to 30 normalized queries in-memory (FIFO eviction).

### Logcat Filter

```
adb logcat -s FirebaseAI
```

| Log line | Meaning |
|---|---|
| `[search] query="..." catalog=N products` | AI call starting |
| `[search] gemini raw response: [3,7,1]` | Gemini replied with ranked IDs |
| `[search] matched N products: [...]` | Results filtered from catalog |
| `[search] cache hit for "..." → N results` | Served from cache |
| `[search] gemini failed → falling back` | Error — keyword search used |
| `[orderMessage] gemini response: "..."` | AI order message generated |
| `[rankProducts] ranked N IDs: [...]` | Home feed re-ordered |

---

## Feature Flagging

Five independent platforms control features. Unified API: `config/ExperimentManager.kt`.

### Platform Enums

| Enum | Platform | File |
|---|---|---|
| `FeatureFlag` | Firebase Remote Config | `config/RemoteConfigManager.kt` |
| `FeatureGate` | Statsig | `config/FeatureGate.kt` |
| `PostHogFlag` | PostHog | `config/PostHogFlag.kt` |
| `BrazeFlag` | Braze | `config/BrazeFlag.kt` |
| `AmplitudeExperimentFlag` | Amplitude Experiment | `config/AmplitudeExperimentFlag.kt` |

### Firebase Remote Config

**Fetch behavior:**
- Debug: 30s (rapid iteration)
- Release: 3600s (1-hour cache)

Values activate in the same session — no second launch needed.

### Amplitude Experiment

**Deployment key:** `BuildConfig.AMPLITUDE_EXPERIMENT_DEPLOYMENT_KEY`

**Initialization:** `Experiment.initializeWithAmplitudeAnalytics()` in `ExperimentManager`

### PostHog Feature Flags

Flags reload after `identify()` — call `experiments.reloadPostHogFlags()`.

**Add new variants without code change:** PostHog → open flag → Add variant → set value → Save. App renders whatever string PostHog returns.

### Braze Feature Flags

Require identified user. Call `experiments.refreshBrazeFeatureFlags()` after login.

### Statsig Feature Gates

Gates are **SDK-level kill switches only** — each gate controls whether an entire analytics SDK sends data.

---

## Session Replay

Four SDKs record sessions with coordinated 40% sampling and unified masking.

| SDK | Sample rate | Mode | `maskView()` impl |
|---|---|---|---|
| Amplitude | 40% | Full fidelity | `SessionReplayPlugin.mask(view)` |
| Mixpanel | 40% | Full fidelity | `SensitiveViewManager.addSensitiveView(view)` |
| PostHog | 40% | Screenshot | `ph-no-capture` in `contentDescription` |
| Clarity | 100% | Full fidelity + heatmaps | `Clarity.maskView(view)` |

### Amplitude Specifics

- `SessionReplayPlugin` attached lazily on first event after `sessionId > 0`
- All `ImageView`s forced to `LAYER_TYPE_SOFTWARE`
- Session ID forwarded to Clarity as `amplitude_session_id`

### Uniform Masking

One call, all four SDKs:
```kotlin
analyticsManager.maskView(binding.tvUserEmail)
```

### PII Masked by Default

- All `EditText` / `TextInputEditText`
- Views tagged `android:tag="ph-no-capture"` in XML
- Views passed to `maskView()` (avatar, email, order ID, etc.)

---

## Deep Link Handling

### Supported URI Schemes

| Scheme | Destination |
|---|---|
| `marketapp://home` | Home tab |
| `marketapp://search` | Search tab |
| `marketapp://cart` | Cart |
| `marketapp://product/{productId}` | Product detail |
| `https://YOUR_DOMAIN/*` | HTTPS App Links |
| `https://marketapp.onelink.me/*` | AppsFlyer OneLink |

### Attribution Query Parameters

| Parameter | Description |
|---|---|
| `utm_source` | Required for `campaign_open` |
| `utm_medium` | Channel (push, email, social) |
| `utm_campaign` | Campaign name |
| `utm_term` | Keyword |
| `utm_content` | Creative ID |
| `deep_link` | Target path (push payload) |
| `campaign_id` | Push campaign ID |

### Attribution Sources

| Source | How it works |
|---|---|
| Cold start | `Intent.data` → NavHostFragment handles navigation + UTM extraction |
| Warm start | `onNewIntent()` → manual `NavDeepLinkRequest` + `NavOptions` |
| Push tap | Intent extras → `PushTapped` + `CampaignOpened` events |
| AppsFlyer OneLink | `AppsFlyerTracker.onDeepLink` callback → `CampaignOpened` + routing |

**All deep links:** `popUpTo(startDestination, inclusive=false)` + `launchSingleTop=true` to prevent back-stack accumulation.

---

## Consent Management

Shown once on first launch via non-cancellable `ConsentBottomSheet`. Two controls: **Analytics** (ON) and **Notifications** (OFF).

One call propagates to all SDKs:
```kotlin
analyticsManager.setAnalyticsConsent(enabled)
```

| SDK | Consent OFF |
|---|---|
| Firebase | Denies `ANALYTICS_STORAGE`, `AD_STORAGE`, etc. |
| Amplitude | `optOut = true` |
| Mixpanel | `optOutTracking()` |
| PostHog | `optOut()` |
| AppsFlyer | `forGDPRUser(false, false)`, `anonymizeUser(true)` |
| Braze | `UNSUBSCRIBED` for push + email |
| OneSignal | `optOut()` |
| Clarity | All tracking suspended |
| Meta | `setAutoLogAppEventsEnabled(false)` |

---

## Push Notifications

Token from `MarketFirebaseMessagingService.onNewToken()` broadcast via:
```kotlin
analyticsManager.onNewPushToken(token)
```

**Required FCM data payload for attribution:**
```json
{
  "campaign_id": "campaign-uuid",
  "deep_link": "marketapp://product/123",
  "utm_source": "braze",
  "utm_medium": "push",
  "utm_campaign": "winback_q1"
}
```

---

## SDK Cross-Integrations

### AppsFlyer → Braze (Audiences)
```kotlin
AppsFlyerLib.getInstance().setPartnerData("braze_inc", mapOf("external_id" to userId))
```

### AppsFlyer → Amplitude (Data enrichment)
```kotlin
val data = mutableMapOf<String, Any>("brazeCustomerId" to Braze.getInstance(context).deviceId)
AmplitudeTracker.deviceId?.let { data["AmplitudeDeviceId"] = it }
AppsFlyerLib.getInstance().setAdditionalData(data)
```

### Clarity → Firebase
Clarity session ID forwarded via `setOnSessionStartedCallback` as custom user property.

### Amplitude → Clarity
Amplitude session ID synced via `Clarity.setCustomTag("amplitude_session_id", ...)`.

---

## Debug Panel

Visible at **Profile → Debug Card** (debug builds only).

| Button | Action |
|---|---|
| SDK & Flag Status | Live view of gates and flags |
| Force Refresh Flags | Calls `RemoteConfigManager.forceRefresh()` + reloads all platforms |
| Amplitude Guide Trigger | Fires `trigger_for_amplitude_guide` |
| Preview Amplitude Guide | Preview URI for Guide ID 41685 |
| Preview Amplitude Survey | Preview URI for Survey ID 41053 |
| Test Push | Fires `trigger_for_pushnotif` → Braze push |
| Test Banner | Fires `trigger_for_banner` → Braze banner |
| Test In-App | Fires `trigger_for_inapp` → Braze in-app |
| Test Content Card | Fires `trigger_for_content_card` → opens sheet |

### SDK & Flag Status Panel

| Section | Platform | Shows |
|---|---|---|
| SDK Gates | Statsig | ACTIVE / OFF per SDK |
| Feature Flags | Firebase RC | Raw values |
| Feature Flags | PostHog | Values from `getFeatureFlag()` |
| Feature Flags | Braze | `enabled` / `unset` |
| Feature Flags | Amplitude Experiment | Variant values |

---

## Server-Side GTM (sGTM)

Firebase Analytics events are routed through a **server-side Google Tag Manager container** instead of going directly to the GA4 endpoint. The app-side change is a single manifest flag — all event forwarding and tag logic lives in the Cloud Run-hosted GTM server container.

### How It Works

```
App  →  Firebase Analytics SDK  →  sGTM Cloud Run container  →  GA4 / other destinations
```

Without sGTM, the SDK sends directly to `www.google-analytics.com/g/collect`. With the flag enabled, traffic is routed via the GA4 property's server-side transport URL, which points to your Cloud Run instance.

### App-Side Configuration

**`AndroidManifest.xml`**

```xml
<!-- Routes Firebase Analytics events through the sGTM container -->
<meta-data
    android:name="google_analytics_sgtm_upload_enabled"
    android:value="true" />

<!-- GTM Preview Activity — scan QR code from GTM server container Preview mode
     to route your device's events through an unpublished container for debugging -->
<activity
    android:name="com.google.firebase.analytics.GoogleAnalyticsServerPreviewActivity"
    android:exported="true"
    android:noHistory="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="tagmanager.sgtm.c.com.marketapp" />
    </intent-filter>
</activity>
```

Set `android:value="false"` to revert to direct GA4 SDK delivery.

### Server-Side Setup (GTM Console)

1. **Create a server container** in GTM → Admin → Create Container → Target platform: Server
2. **Deploy to Cloud Run** — GTM provisions the container URL automatically
3. **Configure the GA4 property** — GA4 Admin → Data Streams → your stream → Measurement Protocol / Server-Side → point to your Cloud Run URL
4. **Add tags** in the server container (GA4, BigQuery, etc.) — the app sends nothing extra

### Preview / Debug Mode

Use the GTM Preview mode QR code (server container → Preview) to route **only your device** through an unpublished container version:

1. Open GTM → your server container → Preview
2. Scan the QR code with the app (triggers `GoogleAnalyticsServerPreviewActivity` via the `tagmanager.sgtm.c.com.marketapp` scheme)
3. Events now flow through the unpublished draft — verify tags fire before publishing

### Debug Panel

The [Debug Panel](#debug-panel) surfaces the current routing mode under the Firebase row:

| Value | Meaning |
|---|---|
| `Active (sGTM)` | `google_analytics_sgtm_upload_enabled = true` |
| `Active (Direct SDK)` | Flag absent or `false` — events go directly to GA4 |

---

## Logcat Reference

**Filter all analytics logs:**
```
adb logcat -s Analytics,FirebaseAI,RemoteConfig,Statsig,SessionReplay
```

### Tags

| Tag | Shows |
|---|---|
| `Analytics` | `track`, `identify`, init results, errors |
| `FirebaseAI` | Search, order message, feed ranking, cache hits |
| `RemoteConfig` | Fetch result, full flag table, per-read values |
| `Statsig` | All gate values in formatted table |
| `SessionReplay` | Which SDKs recording + sample rates |

### Analytics Sub-Prefixes

| Prefix | Source |
|---|---|
| `[DeepLink]` | Deep link routing, UTM attribution |
| `[AF]` | AppsFlyer UID |
| `[AF/EDDL]` | Extended deferred deep link |
| `[AF/UDL]` | Unified deep link |
| `[FCM]` | Firebase Installation ID, FCM token |
| `[DeviceID]` | Android ID, GAID, IMEI |
| `[Facebook]` | Meta App Events SDK |
| `[PostHog]` | PostHog SDK |

### Example Log Output

**Deep links:**
```
[DeepLink] launch=cold  source=native    uri=marketapp://product/123 utm_source=email
[DeepLink] launch=warm  source=push      utm_source=braze campaign_id=abc deep_link=marketapp://home
```

**Remote Config:**
```
D/RemoteConfig: fetch OK — values updated
D/RemoteConfig: ┌─── Remote Config Values ────────────────────────────
D/RemoteConfig: │  ai_search_enabled                        = true
D/RemoteConfig: │  checkout_enabled                         = true
D/RemoteConfig: └─────────────────────────────────────────────────────
```

**Statsig:**
```
D/Statsig: ┌─── Statsig Gate Values ─────────────────────────────
D/Statsig: │  ✓  sdk_amplitude_enabled                    = true
D/Statsig: │  ✗  sdk_posthog_enabled                     = false
D/Statsig: └─────────────────────────────────────────────────────
```

---

## BuildConfig Keys

Set in `local.properties`, injected at build time:

```properties
AMPLITUDE_API_KEY
AMPLITUDE_EXPERIMENT_DEPLOYMENT_KEY
APPSFLYER_DEV_KEY
BRAZE_API_KEY
CLARITY_PROJECT_ID
FACEBOOK_CLIENT_TOKEN
MIXPANEL_TOKEN
ONESIGNAL_APP_ID
POSTHOG_API_KEY
SEGMENT_WRITE_KEY
STATSIG_CLIENT_KEY
ENABLE_ANALYTICS_LOGGING        # true in debug, false in release
```

Firebase reads from `google-services.json` — no entry needed in `local.properties`.

---

## Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />
```

---

## Key File Paths

```
app/src/main/java/com/marketapp/
├─ ai/
│   └─ AiRepository.kt                  Gemini: search, ranking, messages
├─ analytics/
│   ├─ AnalyticsManager.kt              Fan-out coordinator
│   ├─ AnalyticsTracker.kt              Interface + UserProperties
│   ├─ AnalyticsEvent.kt                Event hierarchy + EcommerceItem
│   ├─ AmplitudeTracker.kt              Amplitude + SR + Experiment
│   └─ CoreTrackers.kt                  Facebook, Firebase, PostHog, Mixpanel, etc.
├─ config/
│   ├─ RemoteConfigManager.kt           Firebase RC wrapper
│   ├─ ExperimentManager.kt             Unified wrapper
│   ├─ FeatureGate.kt                   Statsig registry
│   ├─ PostHogFlag.kt                   PostHog registry
│   ├─ BrazeFlag.kt                     Braze registry
│   └─ AmplitudeExperimentFlag.kt       Amplitude Experiment registry
├─ di/
│   └─ AnalyticsModule.kt               Hilt bindings
├─ data/
│   ├─ preferences/AppPreferences.kt    Consent, analytics toggle
│   └─ repository/                      Auth, Cart, Product, etc.
└─ ui/
    ├─ consent/ConsentBottomSheet.kt    GDPR consent sheet
    ├─ profile/
    │   ├─ ProfileFragment.kt
    │   ├─ ContentCardsBottomSheet.kt
    │   └─ DebugInfoBottomSheet.kt      SDK & Flag Status (DEBUG)
    └─ ...                              Other fragments
```

---

## Revenue Tracking

Revenue events sent **per line item** to seven SDKs:

| SDK | Method |
|---|---|
| Amplitude | `Revenue` object per item |
| Mixpanel | `trackCharge(value)` |
| AppsFlyer | `af_purchase` per item |
| Braze | `logPurchase(productId, currency, price, qty)` |
| Firebase | GA4 `purchase` with `items[]` |
| OneSignal | Outcome `purchase` |
| Meta | `fb_mobile_purchase` |

---

## Navigation

| Fragment | Deep link | Bottom nav |
|---|---|---|
| `homeFragment` | `marketapp://home` | ✓ |
| `searchFragment` | `marketapp://search` | ✓ |
| `cartFragment` | `marketapp://cart` | ✓ |
| `profileFragment` | — | ✓ |
| `productDetailFragment` | `marketapp://product/{productId}` | — |
| `checkoutFragment` | — | — |
| `orderConfirmationFragment` | — | — |

---

## License & Usage

Free to use for personal learning, testing, and demo purposes. Attribution appreciated.

**This is a sample app.** SDKs, versions, and implementations may change over time. Use for learning and reference only — not for production deployments.

---

**Made by Sandi Utomo**  
[![LinkedIn](https://img.shields.io/badge/LinkedIn-0A66C2?style=flat-square&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/sandiutomo/)
[![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white)](https://github.com/sandiutomo)