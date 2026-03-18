![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![minSdk](https://img.shields.io/badge/Min%20SDK-26-4285F4?style=flat-square)
![targetSdk](https://img.shields.io/badge/Target%20SDK-35-4285F4?style=flat-square)
![MVVM](https://img.shields.io/badge/Architecture-MVVM-orange?style=flat-square)
![Hilt](https://img.shields.io/badge/DI-Hilt-FF6F00?style=flat-square)
![MIT](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)
![Status](https://img.shields.io/badge/Status-Production%20Patterns-5A6AE8?style=flat-square)

---

# MarketApp — Android Analytics Reference

A reference Android e-commerce app demonstrating how to wire multiple analytics and martech SDKs through a single `AnalyticsManager` fan-out layer — with Firebase Gemini AI layered on top for semantic search, personalized home feed ranking, and AI-generated order messages.

---

## What's inside

| Capability | Detail |
|---|---|
| Analytics fan-out | 11 SDKs initialized in parallel, events dispatched to all with 200 ms dedup and `SupervisorJob` fault isolation |
| Session replay | Amplitude + Mixpanel + PostHog + Clarity — coordinated 40% sampling, unified PII masking |
| Attribution | AppsFlyer OneLink, HTTPS App Links, custom URI scheme, push tap UTM — all cold/warm/hot start cases handled |
| Feature flagging | Firebase Remote Config, Statsig, PostHog, Braze, and Amplitude Experiment — all surfaced in the debug panel |
| Firebase AI (Gemini) | Semantic search, home feed ranking, order confirmation messages — all behind Remote Config kill switches |
| Consent | GDPR-aware opt-out propagated uniformly to all 11 SDKs on a single `setAnalyticsConsent()` call |
| Debug panel | Live SDK gate status, feature flag values, and QA trigger buttons — visible in Profile on debug builds |

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

**Key guarantees**

- All `track()` calls join `initJob` before dispatching — no events fire before init completes.
- 200 ms deduplication prevents double-fire on rapid re-renders.
- `SupervisorJob` — one SDK crashing or timing out does not block the others.

**Platform-exclusive event routing**

Some events target only one platform. `AnalyticsEvent` exposes:
- `isBrazeOnly` — TriggerPushTest, TriggerBannerTest, TriggerInAppTest, TriggerContentCardTest
- `isAmplitudeOnly` — TriggerAmplitudeGuide

Each of the other 10 trackers skips both; BrazeTracker skips `isAmplitudeOnly`; AmplitudeTracker skips `isBrazeOnly`.

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
![Segment](https://img.shields.io/badge/Segment-1.20-52BD95?style=flat-square)
![Meta](https://img.shields.io/badge/Meta%20App%20Events-18.0-0866FF?style=flat-square&logo=meta&logoColor=white)

![Braze](https://img.shields.io/badge/Braze-33.1-FF6B6B?style=flat-square)
![OneSignal](https://img.shields.io/badge/OneSignal-5.6-E54B4D?style=flat-square)
![Statsig](https://img.shields.io/badge/Statsig-4.41-194BFB?style=flat-square)
![Clarity](https://img.shields.io/badge/Microsoft%20Clarity-3.8-00A4EF?style=flat-square&logo=microsoft&logoColor=white)

| SDK | Version | Role |
|---|---|---|
| Firebase Analytics + Crashlytics + AI | BOM 33.14.0 | GA4 events, crash reporting, Gemini generative AI |
| Amplitude | 1.26.0 + Session Replay 0.24.1 + Experiment 1.13.1 | Product analytics, LTV, session replay, A/B experiments |
| Mixpanel | 8.3.0 + Session Replay 1.0.6 | Funnel analysis, people profiles, session replay |
| PostHog | 3.7.1 | Feature flags, session replay |
| AppsFlyer | 6.15.2 | Mobile attribution, deep links, ROAS |
| Braze | 33.1.0 | CRM, push, in-app messages, content cards, banners. Requires both `android-sdk-ui` + `android-sdk-location` for geofence/location support. |
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

All events use GA4 standard names and parameter keys wherever a standard exists. Platform-specific behavior (GA4 Bundle structure, Mixpanel `trackCharge`, AppsFlyer `AFInAppEventType`) lives inside each tracker — the call site always uses `AnalyticsEvent`.

### App lifecycle

| Event | Key properties |
|---|---|
| `app_open` | — |
| `app_background` | — |
| `screen_view` | `screen_name`, `screen_class` |

### Auth & onboarding

| Event | Key properties |
|---|---|
| `onboarding_completed` | `method` |
| `login` | `method` (`email` / `google`) |
| `sign_up` | `method` |
| `user_signed_out` | `session_duration_ms` |

### Product discovery

| Event | Key properties |
|---|---|
| `search` | `search_term`, `result_count` |
| `search_result_tapped` | `item_id`, `position` |
| `select_content` | `category`, `content_type="category"` |
| `view_item_list` | `item_list_id`, `item_list_name`, `items[]` |
| `select_item` | `item_list_id`, `item_list_name`, `item`, `index` |
| `view_promotion` | `promotion_id`, `promotion_name`, `creative_name?`, `creative_slot?`, `location_id?` |
| `select_promotion` | same as `view_promotion` |

### Product detail

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

### Checkout funnel

| Event | Key properties |
|---|---|
| `begin_checkout` | `value`, `currency`, `item_count`, `items[]` |
| `checkout_address_selected` | `address_id`, `city`, `country`, `is_new_address` |
| `add_shipping_info` | `value`, `currency`, `shipping_tier`, `coupon?`, `item_count`, `items[]` |
| `add_payment_info` | `payment_type`, `currency`, `value?`, `items?` |
| `purchase` | `transaction_id`, `value`, `currency`, `coupon?`, `payment_method`, `item_count`, `items[]` |
| `order_failed` | `reason` |
| `refund` | `transaction_id`, `value`, `currency`, `items?` |

### Campaign & attribution

| Event | Key properties |
|---|---|
| `campaign_open` | `source`, `medium?`, `campaign?`, `term?`, `content?`, `deep_link?` |
| `notification_receive` | `campaign_id?`, `type` |
| `notification_open` | `campaign_id?`, `deep_link?` |
| `push_permission_result` | `granted` |

### User profile

| Event | Key properties |
|---|---|
| `address_added` | — |
| `payment_method_added` | — |
| `subscribe` | — |
| `error_occurred` | `screen`, `code`, `message` |

### Debug only (`BuildConfig.DEBUG`)

| Event | Trigger | Reaches |
|---|---|---|
| `trigger_for_pushnotif` | QA panel → Test Push | **Braze only** |
| `trigger_for_banner` | QA panel → Test Banner | **Braze only** |
| `trigger_for_inapp` | QA panel → Test In-App | **Braze only** |
| `trigger_for_content_card` | QA panel → Test Content Card | **Braze only** |
| `trigger_for_amplitude_guide` | QA panel → Amplitude Guide Trigger | **Amplitude only** |

`trigger_*` events are platform-exclusive — enforced via `isBrazeOnly` / `isAmplitudeOnly` on `AnalyticsEvent`. Braze triggers call `braze.requestImmediateDataFlush()` to force immediate campaign evaluation without the normal batch delay.

---

## Data Models

### EcommerceItem

Used in list, cart, checkout, and purchase events:

```kotlin
EcommerceItem(
    itemId       = "sku-123",
    itemName     = "Product Name",
    price        = 199000.0,
    quantity     = 1,
    category     = "Electronics",    // optional
    brand        = "BrandName",      // optional
    variant      = "Blue/XL",        // optional
    discount     = 10000.0,          // optional
    index        = 0,                // list position — required for view_item_list / select_item
    coupon       = "SAVE10",         // optional
    itemListId   = "home_feed",      // optional
    itemListName = "Home Feed"       // optional
)
```

### UserProperties

Set on sign-in via `analyticsManager.identify()`:

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
    loginMethod       = "email",        // or "google"
    hasPurchased      = true,
    orderCount        = 3,
    lifetimeValue     = 750000.0,
    preferredCategory = "Electronics",
    deviceId          = "...",          // ANDROID_ID
    appSetId          = "...",          // Google App Set ID
    advertisingId     = "...",          // GAID — null if LAT enabled
    customAttributes  = mapOf("tier" to "gold")
)
```

---

## Revenue Tracking

Revenue events are sent **per line item** to seven SDKs for accurate ROAS and LTV breakdowns:

| SDK | Method |
|---|---|
| Amplitude | `Revenue` object per item (`productId`, `price`, `quantity`, `revenueType="purchase"`) |
| Mixpanel | `trackCharge(value)` on People profile |
| AppsFlyer | `af_purchase` in-app event per item |
| Braze | `logPurchase(productId, currency, price, qty)` per item |
| Firebase | GA4 `purchase` with `items[]` array |
| OneSignal | Outcome `purchase` with revenue value |
| Meta | `fb_mobile_purchase` with value + currency |

---

## Firebase AI (Gemini)

Powered by `firebase-ai` (BOM 33.14.0), backend: Google AI, model: `gemini-2.0-flash`. All three features use `runCatching` — any API failure falls back silently without crashing.

| Feature | Remote Config flag | Trigger | Fallback |
|---|---|---|---|
| Order confirmation message | `order_ai_message_enabled` | `placeOrder()` | Empty string |
| Home feed ranking | `product_recommendations_enabled` | `HomeViewModel` on load, when user has order history | Default product order |
| Semantic search | `ai_search_enabled` | Keyboard Search / IME action, queries ≥ 4 chars | Keyword search |

**Product order randomization:** The home product feed is shuffled on every load (`.shuffled()` in `HomeViewModel`) before AI ranking is applied. This prevents the same order appearing on every session when AI ranking is not active.

**Search has two tiers:**
- **Typing** (debounced 800 ms, ≥ 2 chars) — keyword search only, zero AI calls.
- **IME Search key** — AI semantic search, one Gemini call per submit.

**Result cache:** `AiRepository` caches up to 30 normalized queries in-memory (FIFO eviction). Repeat queries return instantly with no API call.

**Logcat filter:** `adb logcat -s FirebaseAI`

| Log line | Meaning |
|---|---|
| `[search] query="..." catalog=N products` | AI call starting |
| `[search] gemini raw response: [3,7,1]` | Gemini replied with ranked IDs |
| `[search] matched N products: [...]` | Results filtered from catalog |
| `[search] cache hit for "..." → N results` | Served from cache, no API call |
| `[search] gemini failed → falling back` | Error — keyword search used instead |
| `[orderMessage] gemini response: "..."` | AI order message generated |
| `[rankProducts] ranked N IDs: [...]` | Home feed re-ordered by Gemini |

---

## Deep Link Handling

### Supported URI schemes

| Scheme | Destination |
|---|---|
| `marketapp://home` | Home tab |
| `marketapp://search` | Search tab |
| `marketapp://cart` | Cart |
| `marketapp://product/{productId}` | Product detail |
| `https://YOUR_DOMAIN/*` | HTTPS App Links (`autoVerify`) |
| `https://marketapp.onelink.me/*` | AppsFlyer OneLink (`autoVerify`) |

### Attribution query parameters

| Parameter | Description |
|---|---|
| `utm_source` | Required for `campaign_open` event |
| `utm_medium` | Channel — push, email, social, etc. |
| `utm_campaign` | Campaign name |
| `utm_term` | Keyword term |
| `utm_content` | Creative identifier |
| `deep_link` | Target path (push payload) |
| `campaign_id` | Push campaign ID |

### Attribution sources

| Source | How it works |
|---|---|
| Cold start | `Intent.data` — NavHostFragment handles navigation; app extracts UTM only |
| Warm start | `onNewIntent()` → manual `NavDeepLinkRequest` + `NavOptions` |
| Push tap | Intent extras (`campaign_id`, `deep_link`, `utm_*`) → fires `PushTapped` + `CampaignOpened` |
| AppsFlyer OneLink | `AppsFlyerTracker.onDeepLink` callback → fires `CampaignOpened`, routes to destination |

All deep links use `popUpTo(startDestination, inclusive=false)` + `launchSingleTop=true` to prevent back-stack accumulation. Unrecognized URIs fall back to home.

---

## Session Replay

Four SDKs record sessions with coordinated sampling and a single masking API.

| SDK | Sample rate | Mode | `maskView()` implementation |
|---|---|---|---|
| Amplitude | 40% | Full fidelity | `SessionReplayPlugin.mask(view)` |
| Mixpanel | 40% | Full fidelity | `SensitiveViewManager.addSensitiveView(view)` |
| PostHog | 40% | Screenshot | Appends `ph-no-capture` to `contentDescription` |
| Clarity | 100% | Full fidelity + heatmaps | `Clarity.maskView(view)` |

**Amplitude specifics:**
- `SessionReplayPlugin` is attached lazily on first event after `sessionId > 0`.
- All `ImageView`s are forced to `LAYER_TYPE_SOFTWARE` to prevent rendering corruption in replay.
- Session ID is forwarded to Clarity as `amplitude_session_id` for cross-SDK lookup.

**Uniform masking — one call, all four SDKs:**
```kotlin
analyticsManager.maskView(binding.tvUserEmail)
```

**PII masked by default:**
- All `EditText` / `TextInputEditText` (Mixpanel + PostHog config).
- Views tagged `android:tag="ph-no-capture"` in XML.
- Views passed to `maskView()` in code — avatar, username, email, order ID, etc.

---

## Push Notifications

Token from `MarketFirebaseMessagingService.onNewToken()` is broadcast to all SDKs via `analyticsManager.onNewPushToken(token)`.

| SDK | Push capability |
|---|---|
| Braze | Rich push, push stories, push primer |
| OneSignal | Standard push, in-app messages |
| Firebase | Baseline FCM delivery |
| AppsFlyer | Uninstall detection via silent push |

**Required FCM data payload keys for attribution:**

```json
{
  "campaign_id": "campaign-uuid",
  "deep_link":   "marketapp://product/123",
  "utm_source":  "braze",
  "utm_medium":  "push",
  "utm_campaign": "winback_q1"
}
```

---

## Consent Management

Shown once on first launch via a non-cancellable `ConsentBottomSheet`. Two controls: **Analytics** (default ON) and **Notifications** (default OFF). On continue, `setAnalyticsConsent()` propagates to all SDKs simultaneously.

| SDK | Consent OFF behaviour |
|---|---|
| Firebase | Denies `ANALYTICS_STORAGE`, `AD_STORAGE`, `AD_USER_DATA`, `AD_PERSONALIZATION` |
| Amplitude | `configuration.optOut = true` |
| Mixpanel | `optOutTracking()` |
| PostHog | `optOut()` |
| AppsFlyer | `forGDPRUser(false, false)`, `anonymizeUser(true)` |
| Braze | `setPushNotificationSubscriptionType(UNSUBSCRIBED)` + `setEmailNotificationSubscriptionType(UNSUBSCRIBED)` |
| OneSignal | `pushSubscription.optOut()` |
| Clarity | All tracking suspended |
| Meta | `setAutoLogAppEventsEnabled(false)`, `setAdvertiserIDCollectionEnabled(false)` |

**Braze subscription groups:**

| Group | ID |
|---|---|
| WhatsApp | `36bba8fd-c772-4ca2-8a83-81bbc411501d` |
| Email | `8cd50cfa-c961-4d42-afc0-348bf9772c18` |

---

## SDK Cross-Integrations

### AppsFlyer → Braze (Audiences)

```kotlin
// In AppsFlyerTracker.identify()
AppsFlyerLib.getInstance().setPartnerData(
    "braze_inc", mapOf("external_id" to userId)
)
```

### AppsFlyer → Amplitude (Data enrichment)

```kotlin
// In AppsFlyerTracker.onSessionStart()
// setAdditionalData replaces all keys on each call — always pass a complete map
val data = mutableMapOf<String, Any>(
    "brazeCustomerId" to Braze.getInstance(context).deviceId
)
AmplitudeTracker.deviceId?.let  { data["AmplitudeDeviceId"]  = it }
AmplitudeTracker.sessionId
    .takeIf { it > 0 }
    ?.let   { data["AmplitudeSessionId"] = it.toString() }
AppsFlyerLib.getInstance().setAdditionalData(data)
```

### Clarity → Firebase

Clarity session ID forwarded to Firebase via `setOnSessionStartedCallback` as a custom user property — enables session replay lookup directly from Crashlytics.

### Amplitude → Clarity

Amplitude session ID synced to Clarity via `Clarity.setCustomTag("amplitude_session_id", ...)` — updated on SDK init and on every `identify()` call.

---

## Feature Flagging

Five platforms control features independently. Full dashboard setup is in `docs/feature-flagging-dashboard-guide.md`. Cross-reference `docs/feature-flag-tracker.md` for the complete flag list with UX impact. The unified API for all platforms is `config/ExperimentManager.kt`.

### Feature flag enums by platform

| Enum | Platform | File |
|---|---|---|
| `FeatureFlag` | Firebase Remote Config | `config/RemoteConfigManager.kt` |
| `FeatureGate` | Statsig | `config/FeatureGate.kt` |
| `PostHogFlag` | PostHog | `config/PostHogFlag.kt` |
| `BrazeFlag` | Braze | `config/BrazeFlag.kt` |
| `AmplitudeExperimentFlag` | Amplitude Experiment | `config/AmplitudeExperimentFlag.kt` |

---

## Remote Config

`RemoteConfigManager.fetchAndActivate()` is called in `MarketApplication.onCreate()`. Values activate in the same session — no second launch needed.

| Build | Fetch interval | Effect |
|---|---|---|
| Debug | 30 s | Changes picked up quickly after kill + reopen |
| Release | 3600 s | Up to 1-hour cache |

### UX & merchandising flags

| Flag | Type | Default | Purpose |
|---|---|---|---|
| `show_promotions_banner` | Boolean | true | Horizontal promotions carousel at top of home feed. Flip to `false` globally when no promotion is live. |
| `search_autocomplete_enabled` | Boolean | true | Live autocomplete suggestions while typing in the search bar. |
| `wishlist_enabled` | Boolean | true | Heart/wishlist button on product cards and PDP. Disable to remove the feature entirely without a release. |

### Kill switches

All default to `true` so the feature works offline and before the first fetch. Set to `false` to disable instantly for all users.

| Flag | Type | Default | What it kills |
|---|---|---|---|
| `payment_method_cod_enabled` | Boolean | true | Cash-on-Delivery option on the payment screen. Can be targeted by locale condition to disable by region. |
| `firestore_write_enabled` | Boolean | true | Firestore order writes after a successful purchase. |
| `ai_order_message_enabled` | Boolean | true | Gemini-generated thank-you message on order confirmation. Screen still appears normally; message area is empty when disabled. |
| `ai_search_enabled` | Boolean | true | Gemini semantic re-ranking on IME Search key press. Falls back to keyword search — no visible degradation to the user. |
| `view_item_list_enabled` | Boolean | true | Gates `view_item_list` + `select_item` analytics events on home feed and search. Disable to suppress product-list events globally without a release. |
| `request_refund_enabled` | Boolean | true | Shows the "Request Refund" button on order confirmation. Toggle off to pause the refund flow. |

### Feature rollouts

| Flag | Type | Default | Purpose |
|---|---|---|---|
| `ai_product_sorting_enabled` | Boolean | true | Gemini-based home feed ranking using recent order history. Only fires when the user has at least one previous order. Use a percentage audience condition for gradual rollout. |

**Gradual rollout via audience condition:**
1. Remote Config → Conditions → Add condition → Name: `10% rollout` → Rule: User in random percentile ≤ 10
2. Set `ai_product_sorting_enabled = true` for that condition, keep default = `false`
3. Increase the percentile over time as metrics look healthy

### Business configuration values

Numeric parameters tunable without a release. The app picks up new values on next launch (or after the fetch interval).

| Flag | Type | Default | Purpose |
|---|---|---|---|
| `free_shipping_threshold_idr` | Double | 5000000 | Minimum cart total (IDR) for free shipping. Orders below this are charged Rp 150.000 flat. Set `0` for a specific audience to make shipping always free. |
| `max_shipping_days` | Long | 7 | Upper bound of the delivery window shown on order confirmation (e.g. "Delivered in 3–7 days"). Update during peak seasons without a release. |

**LTV-based free shipping example** — `free_shipping_threshold_idr = 0` for high-LTV users:
1. Remote Config → Conditions → Add condition → Name: `High LTV` → Rule: User property `ltv_bucket` equals `high`
2. Open `free_shipping_threshold_idr` → Add value for condition → `High LTV` → value: `0`, default: `5000000`
3. Publish

---

## Amplitude Experiment

Deployment key: `BuildConfig.AMPLITUDE_EXPERIMENT_DEPLOYMENT_KEY`. Initialized via `Experiment.initializeWithAmplitudeAnalytics()` in `ExperimentManager` — variants are fetched asynchronously on launch; cached values from the previous session are available immediately.

### dark-mode

| Field | Value |
|---|---|
| Flag key | `dark-mode` |
| Type | Experiment (A/B test) |
| Bucketing unit | User |
| Primary metric | 7-day user retention |
| Secondary metrics | Average session duration, checkout conversion rate |

| Variant | Value | Traffic | Description |
|---|---|---|---|
| `control` | `control` | 50% | Light theme (current default) |
| `treatment` | `treatment` | 50% | Dark theme applied on launch |

The theme is applied before layout inflation in `MainActivity` to avoid a flash of the wrong theme. Users outside the traffic allocation fall back to light theme.

```kotlin
// MainActivity.onCreate(), after super.onCreate()
val isDark = experiments.getAmplitudeVariant(AmplitudeExperimentFlag.DARK_MODE.key) == "treatment"
AppCompatDelegate.setDefaultNightMode(
    if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
)
```

### home_layout

| Field | Value |
|---|---|
| Flag key | `home_layout` |
| Type | Feature flag |
| Purpose | Controls the home screen product grid layout variant. Visible in Debug → SDK & Flag Status panel. |

---

## PostHog Feature Flags

Flags reload after `analytics.identify()` — call `experiments.reloadPostHogFlags()` so flags targeting user properties take effect in the same session.

### home-greeting-variant

| Field | Value |
|---|---|
| Key | `home-greeting-variant` |
| Flag type | Multivariate |
| Purpose | Replaces the time-based home screen greeting with personalized copy. The flag value is rendered directly as the greeting text — no code change needed to add new copy variants. |

| Variant | Value served | Traffic | Description |
|---|---|---|---|
| `control` | `control` | 50% | Time-based fallback ("Good morning / afternoon / evening") |
| `welcome-back` | `Welcome back!` | 25% | Returning user warm greeting |
| `lets-shop` | `Let's find something great` | 25% | Action-oriented copy |

**Adding a new copy variant without a code change:** PostHog → open `home-greeting-variant` → Add variant → set any key (e.g. `ramadan`) and value (e.g. `Ramadan Mubarak`) → assign percentage → Save. The app renders whatever string PostHog returns.

### checkout-progress-bar

| Field | Value |
|---|---|
| Key | `checkout-progress-bar` |
| Flag type | Boolean |
| Purpose | Shows a `LinearProgressIndicator` on both checkout steps (50% on shipping → 100% on payment). Target: users who abandoned checkout (viewed cart but did not purchase). |

**Cohort setup:** PostHog → Cohorts → create filter: `view_cart` fired AND `purchase` never fired in last 30 days → set as release condition for this flag.

---

## Braze Feature Flags

Braze flags are user-segment-driven and require an identified user to resolve correctly. Call `experiments.refreshBrazeFeatureFlags()` after login.

### checkout-cta

| Field | Value |
|---|---|
| ID | `checkout-cta` |
| Purpose | Overrides the "Place Order" button label on the payment screen with a segment-specific string. Use for high-intent segments ("Buy Now"), loyalty members ("Redeem & Buy"), or campaign cohorts ("Claim Offer") without a release. |
| Rollout | Start at 0%, then target a segment |

| Property key | Type | Example | Purpose |
|---|---|---|---|
| `label` | String | `Buy Now` | Button text to render. If empty or missing, falls back to "Place Order". |

```kotlin
// PaymentFragment.onViewCreated()
val flag = experiments.getBrazeFeatureFlag(BrazeFlag.CHECKOUT_CTA.key)
if (flag?.enabled == true) {
    flag.getStringProperty("label")?.let { binding.placeOrderBtn.text = it }
}
```

**Segment targeting example — high-intent users:**
1. Braze → Segments → Create segment → Filter: Custom event `Product Detail Viewed` count ≥ 3 in last 7 days
2. Open `checkout-cta` → Target users by segment → set rollout to 100% for that segment

---

## Statsig Feature Gates

Gates are SDK-level kill switches only — each gate controls whether an entire analytics SDK sends data. These are operational safety valves, not product features.

> **⚠ Important:** A gate key that does not exist in Statsig returns `false` by default — that SDK is silently disabled for all users. Create all gates with Pass% = 100 in both Development and Production before deploying.

### Gate registry (`FeatureGate.kt`)

All gates are centralized here. Adding a gate automatically includes it in logcat startup dumps and the debug panel.

| Gate key | What it kills | Notes |
|---|---|---|
| `sdk_amplitude_enabled` | Amplitude events + session replay | — |
| `sdk_facebook_enabled` | Meta App Events attribution | — |
| `sdk_firebase_enabled` | GA4 event stream | Does not affect Crashlytics — crash reporting continues |
| `sdk_posthog_enabled` | PostHog events + session replay | — |
| `sdk_mixpanel_enabled` | Mixpanel events + People profiles | — |
| `sdk_appsflyer_enabled` | AppsFlyer in-app events | Install attribution (handled natively by SDK) is unaffected |
| `sdk_clarity_enabled` | Clarity recordings + heatmaps | — |
| `sdk_braze_enabled` | Braze custom events | Push delivery and in-app messages are server-side — unaffected |
| `sdk_onesignal_enabled` | OneSignal outcome tracking | Push delivery is unaffected |

**Emergency disable:** Statsig Console → open the gate → set Pass% to `0` → Save. Takes effect within ~10 s. No app release required. Re-enable by setting Pass% back to `100`.

---

## Quick Reference — All Flags

| Key | Platform | Type |
|---|---|---|
| `show_promotions_banner` | Firebase RC | Boolean |
| `search_autocomplete_enabled` | Firebase RC | Boolean |
| `wishlist_enabled` | Firebase RC | Boolean |
| `payment_method_cod_enabled` | Firebase RC | Boolean |
| `firestore_write_enabled` | Firebase RC | Boolean |
| `ai_order_message_enabled` | Firebase RC | Boolean |
| `ai_search_enabled` | Firebase RC | Boolean |
| `ai_product_sorting_enabled` | Firebase RC | Boolean |
| `view_item_list_enabled` | Firebase RC | Boolean |
| `request_refund_enabled` | Firebase RC | Boolean |
| `free_shipping_threshold_idr` | Firebase RC | Double |
| `max_shipping_days` | Firebase RC | Long |
| `dark-mode` | Amplitude Experiment | Experiment |
| `home_layout` | Amplitude Experiment | Feature flag |
| `home-greeting-variant` | PostHog | Multivariate flag |
| `checkout-progress-bar` | PostHog | Boolean flag |
| `checkout-cta` | Braze | Feature flag + properties |
| `sdk_amplitude_enabled` | Statsig | Feature gate |
| `sdk_facebook_enabled` | Statsig | Feature gate |
| `sdk_firebase_enabled` | Statsig | Feature gate |
| `sdk_posthog_enabled` | Statsig | Feature gate |
| `sdk_mixpanel_enabled` | Statsig | Feature gate |
| `sdk_appsflyer_enabled` | Statsig | Feature gate |
| `sdk_clarity_enabled` | Statsig | Feature gate |
| `sdk_braze_enabled` | Statsig | Feature gate |
| `sdk_onesignal_enabled` | Statsig | Feature gate |

---

## Debug QA Panel

Visible at **Profile → Debug Card** on debug builds only.

| Button | Color | Action |
|---|---|---|
| SDK & Flag Status | Gray | Opens `DebugInfoBottomSheet` — live view of all SDK gates and feature flags |
| Force Refresh Flags | Orange | Calls `RemoteConfigManager.forceRefresh()` + reloads Statsig, PostHog, Braze, and Amplitude flags |
| Amplitude Guide Trigger | Amplitude Blue | Fires `trigger_for_amplitude_guide` → triggers an Amplitude Guide campaign |
| Preview Amplitude Guide | Light Blue | Calls `AmplitudeTracker.handleLinkIntentWhenReady()` with preview URI for Guide ID 41685 |
| Preview Amplitude Survey | Light Blue | Calls `AmplitudeTracker.handleLinkIntentWhenReady()` with preview URI for Survey ID 41053 |
| Test Push | Braze Purple | Fires `trigger_for_pushnotif` → Braze sends test push |
| Test Banner | Braze Purple | Fires `trigger_for_banner` → Braze shows banner campaign |
| Test In-App | Braze Purple | Fires `trigger_for_inapp` → Braze shows in-app message |
| Test Content Card | Braze Purple | Fires `trigger_for_content_card` + opens `ContentCardsBottomSheet` |

#### Amplitude Guides & Surveys

- `eng?.screen(destination)` is called on every navigation change in `MainActivity` — Amplitude uses this to evaluate guide triggers against the user's current screen.
- Guide/Survey preview deep links use `amp-<deploymentKey>://gs/preview/<contentId>` URI scheme.
- `AmplitudeTracker.handleLinkIntentWhenReady(intent)` queues the intent if the engagement SDK hasn't initialized yet (cold-start race), then dispatches once ready.
- Update the preview URI constants in `ProfileFragment.kt` to match your own Guide/Survey IDs.

### SDK & Flag Status panel

| Section | Platform | Shows |
|---|---|---|
| SDK Gates | Statsig | ACTIVE / OFF per analytics SDK |
| Feature Rollouts | Statsig | ACTIVE / OFF per rollout gate |
| Feature Flags | Firebase Remote Config | Raw value per flag |
| Feature Flags | PostHog | Value from `PostHog.getFeatureFlag()` |
| Feature Flags | Braze | `enabled` / `unset` from `Braze.getFeatureFlag()` |
| Feature Flags | Amplitude Experiment | Variant value from `ExperimentClient.variant()` |

A **Refresh** button re-reads all values live — useful after toggling a flag in any dashboard.

---

## Logcat Reference

**Filter all analytics logs:**
```
adb logcat -s Analytics,FirebaseAI,RemoteConfig,Statsig,SessionReplay
```

### Tags

| Tag | Fires on | Shows |
|---|---|---|
| `Analytics` | Every event dispatch | `track`, `identify`, init results, SDK errors |
| `FirebaseAI` | Every Gemini call | Search, order message, feed ranking — cache hits, raw responses, fallbacks |
| `RemoteConfig` | App start + every flag read | Fetch result, full flag table, per-read values |
| `Statsig` | App start | All gate values in a formatted table |
| `SessionReplay` | App start | Which SDKs are recording and at what sample rate |

### `Analytics` sub-prefixes

| Prefix | Source |
|---|---|
| `[DeepLink]` | Deep link routing and UTM attribution |
| `[AF]` | AppsFlyer UID |
| `[AF/EDDL]` | Extended deferred deep link (fresh install) |
| `[AF/UDL]` | Unified deep link (app already installed) |
| `[FCM]` | Firebase Installation ID and FCM token |
| `[DeviceID]` | Android ID, GAID, IMEI — for AppsFlyer test device registration |
| `[Facebook]` | Meta App Events SDK |
| `[PostHog]` | PostHog SDK |

### Example log output

**Deep links:**
```
[DeepLink] launch=cold  source=native    uri=marketapp://product/123 utm_source=email
[DeepLink] launch=warm  source=push      utm_source=braze campaign_id=abc deep_link=marketapp://home
[DeepLink] launch=warm  source=appsflyer utm_source=email campaign=winback deep_link=marketapp://product/99
[DeepLink] no destination launch=hot     uri=marketapp://unknown — falling back to home
```

**Remote Config startup dump:**
```
D/RemoteConfig: fetch OK — values updated
D/RemoteConfig: ┌─── Remote Config Values ────────────────────────────
D/RemoteConfig: │  ai_search_enabled                        = true
D/RemoteConfig: │  checkout_enabled                         = true
D/RemoteConfig: └─────────────────────────────────────────────────────
```
Any value marked `[default]` means the key doesn't exist in Firebase Console yet — only the local default from `remote_config_defaults.xml` is active.

**Statsig startup dump:**
```
D/Statsig: ┌─── Statsig Gate Values ─────────────────────────────
D/Statsig: │  ✓  sdk_amplitude_enabled                    = true
D/Statsig: │  ✗  sdk_posthog_enabled                     = false
D/Statsig: └─────────────────────────────────────────────────────
```
Any gate showing `✗ = false` that you didn't intentionally disable means the gate hasn't been created in Statsig Console yet — create it with Pass% = 100.

---

## Navigation

| Fragment | Deep link | Bottom nav |
|---|---|---|
| `homeFragment` | `marketapp://home` | ✓ |
| `searchFragment` | `marketapp://search` | ✓ |
| `cartFragment` | `marketapp://cart` | ✓ |
| `profileFragment` | — | ✓ |
| `productDetailFragment` | `marketapp://product/{productId}` | — |
| `categoryFragment` | — | — |
| `checkoutFragment` | — | — |
| `orderConfirmationFragment` | — | — |

---

## BuildConfig Keys

Set in `local.properties`, injected at build time via `buildConfigField`:

```
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
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />        <!-- Braze geofences -->
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />  <!-- Braze geofences API 29+ -->
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />        <!-- API 31+ -->
```

---

## Key File Paths

```
app/src/main/java/com/marketapp/
├─ ai/
│   └─ AiRepository.kt                  Gemini: semantic search, feed ranking, order message
├─ analytics/
│   ├─ AnalyticsManager.kt              Fan-out coordinator — init, track, identify, consent
│   ├─ AnalyticsTracker.kt              Tracker interface + UserProperties data class
│   ├─ AnalyticsEvent.kt                Sealed event hierarchy + EcommerceItem + toProperties()
│   ├─ AmplitudeTracker.kt              Amplitude + Session Replay + SessionReplayLogger
│   └─ CoreTrackers.kt                  Facebook, Firebase, PostHog, Mixpanel, AppsFlyer,
│                                       Clarity, Braze, OneSignal
├─ config/
│   ├─ RemoteConfigManager.kt           Firebase Remote Config wrapper + FeatureFlag enum
│   ├─ ExperimentManager.kt             Unified wrapper: RC, Statsig, PostHog, Braze, Amplitude
│   ├─ FeatureGate.kt                   Statsig gate registry (SDK + rollout categories)
│   ├─ PostHogFlag.kt                   PostHog flag registry
│   ├─ BrazeFlag.kt                     Braze flag registry
│   └─ AmplitudeExperimentFlag.kt       Amplitude Experiment flag registry
├─ di/
│   └─ AnalyticsModule.kt               Hilt @IntoSet bindings for Set<AnalyticsTracker>
├─ data/
│   ├─ preferences/AppPreferences.kt    consentShown, analyticsEnabled, notificationsEnabled
│   └─ repository/                      Auth, Cart, Product, DeviceInfo, UserStats
├─ ui/
│   ├─ consent/ConsentBottomSheet.kt    First-launch GDPR consent sheet
│   ├─ home/                            HomeFragment, SearchFragment, adapters
│   ├─ profile/
│   │   ├─ ProfileFragment.kt
│   │   ├─ ContentCardsBottomSheet.kt
│   │   └─ DebugInfoBottomSheet.kt      SDK & Flag Status panel (DEBUG only)
│   ├─ product/                         ProductDetailFragment + ViewModel
│   ├─ cart/                            CartFragment + ViewModel
│   └─ checkout/                        CheckoutFragment, PaymentFragment, ConfirmationFragment
└─ MainActivity.kt                      Deep link routing, Braze IAM lifecycle
```

---

## License & Usage

This project is built as a learning reference for Android analytics and martech integration patterns.

You are free to use, modify, and distribute this code for **personal learning, testing, and demo purposes**. If you build on this work, attribution is appreciated but not required.

> This is not a production application. API keys, SDK credentials, and endpoint configurations must be supplied via `local.properties` and are intentionally excluded from version control.
 
---
## Author

**Sandi Utomo**

[![LinkedIn](https://img.shields.io/badge/LinkedIn-0A66C2?style=flat-square&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/sandiutomo/)
[![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white)](https://github.com/sandiutomo)