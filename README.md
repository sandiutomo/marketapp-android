# MarketApp — Android Analytics Reference

A production-ready Android e-commerce app demonstrating a full analytics stack with 11 SDKs wired together through a single `AnalyticsManager` fan-out layer.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| DI | Hilt |
| Architecture | MVVM + Jetpack Navigation |
| Min SDK | 26 |
| Target/Compile SDK | 35 |

---

## Analytics SDKs

| SDK | Version | Role |
|---|---|---|
| Firebase Analytics + Crashlytics | BOM 33.7.0 | GA4 standard events, crash reporting |
| Amplitude | 1.26.0 + Session Replay 0.24.1 | Product analytics, revenue LTV, session replay |
| Mixpanel | 8.3.0 + Session Replay 1.0.6 | Funnel analysis, people profiles, session replay |
| PostHog | 3.7.1 | Feature flags, session replay (wireframe) |
| AppsFlyer | 6.15.2 | Mobile attribution, deep link, ROAS |
| Braze | 33.0.0 | CRM, push, in-app messages, content cards |
| OneSignal | 5.6.2 | Push, in-app, outcomes/conversions |
| Microsoft Clarity | 3.8.1 | Heatmaps, session recordings |
| Facebook App Events | 18.0.3 | Meta pixel, conversion API |
| Segment | 1.20.0 | Data pipeline (CDP layer) |
| Statsig | 4.41.0 | Feature flags, A/B experiments |

---

## Architecture Overview

```
MarketApplication.onCreate()
        │
        ▼
AnalyticsManager.initialize()         ← called once, async parallel init
        │  (Dispatchers.IO, SupervisorJob)
        ├─ FirebaseTracker
        ├─ AmplitudeTracker
        ├─ MixpanelTracker
        ├─ PostHogTracker
        ├─ AppsFlyerTracker
        ├─ BrazeTracker
        ├─ OneSignalTracker
        ├─ ClarityTracker
        ├─ FacebookTracker
        ├─ SegmentTracker
        └─ StatsigTracker

UI / ViewModel
        │
        ▼
analyticsManager.track(AnalyticsEvent)   ← fan-out to all 11 SDKs
analyticsManager.identify(userId, props) ← fan-out to all 11 SDKs
analyticsManager.reset()                 ← on sign-out
```

**Key guarantees:**
- All `track()` calls wait for `initJob` (init completion) before dispatching.
- 200 ms event deduplication prevents double-fire on rapid re-renders.
- `SupervisorJob` — one tracker failing does not block others.

---

## AnalyticsManager API

```kotlin
analyticsManager.initialize()
analyticsManager.track(event: AnalyticsEvent)
analyticsManager.identify(userId, properties: UserProperties)
analyticsManager.alias(newId, oldId)
analyticsManager.reset()
analyticsManager.onNewPushToken(token)
analyticsManager.maskView(view)                  // masks PII in session replay
analyticsManager.setAnalyticsConsent(enabled)    // propagates to all SDKs
analyticsManager.trackError(screen, code, msg)
analyticsManager.captureLog(level, message, tag) // PostHog debug log capture
analyticsManager.shutdown()                      // flush before process exit
```

---

## Analytics Events

### App Lifecycle
| Event | Properties |
|---|---|
| `app_open` | — |
| `app_background` | — |
| `screen_view` | `screen_name`, `screen_class` |

### Onboarding & Auth
| Event | Properties |
|---|---|
| `onboarding_completed` | `method` |
| `login` | `method` (`email` / `google`) |
| `sign_up` | `method` |
| `user_signed_out` | `session_duration_ms` |

### Product Discovery
| Event | Properties |
|---|---|
| `search` | `search_term`, `result_count` |
| `search_result_tapped` | `item_id`, `position` |
| `select_content` | `category`, `content_type="category"` |
| `view_item_list` | `item_list_id`, `item_list_name`, `items[]` |
| `select_item` | `item_list_id`, `item_list_name`, `item`, `index` |
| `view_promotion` | `promotion_id`, `promotion_name`, `creative_name?`, `creative_slot?`, `location_id?` |
| `select_promotion` | same as view_promotion |

### Product Detail
| Event | Properties |
|---|---|
| `view_item` | `item_id`, `item_name`, `price`, `item_category`, `currency`, `value`, `source` |
| `product_image_swiped` | `item_id`, `image_index` |
| `share` | `item_id`, `method`, `content_type="product"` |
| `add_to_wishlist` | `item_id`, `item_name`, `price`, `value`, `currency`, `added` |
| `remove_from_wishlist` | same |

### Cart
| Event | Properties |
|---|---|
| `add_to_cart` | `item_id`, `item_name`, `price`, `quantity`, `item_category`, `value`, `currency` |
| `remove_from_cart` | `item_id`, `item_name`, `price`, `quantity`, `value`, `currency` |
| `view_cart` | `value`, `currency`, `item_count`, `items[]` |

### Checkout Funnel
| Event | Properties |
|---|---|
| `begin_checkout` | `value`, `currency`, `item_count`, `items[]` |
| `checkout_address_selected` | `address_id`, `city`, `country`, `is_new_address` |
| `add_shipping_info` | `value`, `currency`, `shipping_tier`, `coupon?`, `item_count`, `items[]` |
| `add_payment_info` | `payment_type`, `currency`, `value?`, `items?` |
| `purchase` | `transaction_id`, `value`, `currency`, `coupon?`, `payment_method`, `item_count`, `items[]` |
| `order_failed` | `reason` |
| `refund` | `transaction_id`, `value`, `currency`, `items?` |

### Campaign & Attribution
| Event | Properties |
|---|---|
| `campaign_open` | `source`, `medium?`, `campaign?`, `term?`, `content?`, `deep_link?` |
| `notification_receive` | `campaign_id?`, `type` |
| `notification_open` | `campaign_id?`, `deep_link?` |
| `push_permission_result` | `granted` |

### User Profile
| Event | Properties |
|---|---|
| `address_added` | — |
| `payment_method_added` | — |
| `subscribe` | — |
| `error_occurred` | `screen`, `code`, `message` |

### Debug-Only (BuildConfig.DEBUG)
| Event | Trigger |
|---|---|
| `trigger_for_pushnotif` | QA panel → Test Push |
| `trigger_for_banner` | QA panel → Test Banner |
| `trigger_for_inapp` | QA panel → Test In-App |
| `trigger_for_content_card` | QA panel → Test Content Card |
| `trigger_for_experiment` | QA panel → Test Experiment |

`trigger_*` events call `braze.requestImmediateDataFlush()` to force immediate campaign evaluation.

---

## EcommerceItem

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
    index        = 0,                // optional, for list position
    coupon       = "SAVE10",         // optional
    itemListId   = "home_feed",      // optional
    itemListName = "Home Feed"       // optional
)
```

---

## User Properties

Set on sign-in via `analyticsManager.identify()`:

```kotlin
UserProperties(
    userId           = "user-uuid",
    email            = "user@example.com",
    name             = "Full Name",
    firstName        = "First",
    lastName         = "Last",
    phone            = "+62xxx",
    country          = "ID",
    currency         = "IDR",
    loginMethod      = "email",          // or "google"
    hasPurchased     = true,
    orderCount       = 3,
    lifetimeValue    = 750000.0,
    preferredCategory = "Electronics",
    deviceId         = "...",
    appSetId         = "...",
    advertisingId    = "...",
    customAttributes = mapOf("tier" to "gold")
)
```

---

## Revenue Tracking

Revenue events are sent **per line item** to multiple SDKs for accurate ROAS and LTV breakdowns:

| SDK | Method |
|---|---|
| Amplitude | `Revenue` object per item (productId, price, quantity, revenueType="purchase") |
| Mixpanel | `trackCharge(value)` on People profile |
| AppsFlyer | `af_purchase` in-app event per item |
| Braze | `logPurchase(productId, currency, price, qty)` per item |
| Firebase | GA4 `purchase` with `items[]` array |
| OneSignal | Outcome `purchase` with revenue value |
| Facebook | `fb_mobile_purchase` with value + currency |

---

## Deep Link Handling

### Supported URI Schemes

| Scheme | Destination |
|---|---|
| `marketapp://home` | Home tab |
| `marketapp://search` | Search tab |
| `marketapp://cart` | Cart |
| `marketapp://product/{productId}` | Product detail |
| `https://YOUR_DOMAIN/*` | HTTPS App Links (autoVerify) |
| `https://marketapp.onelink.me/*` | AppsFlyer OneLink (autoVerify) |

### Attribution Query Parameters (all sources)

| Parameter | Description |
|---|---|
| `utm_source` | Required for `campaign_open` event |
| `utm_medium` | Channel (push / email / social / etc.) |
| `utm_campaign` | Campaign name |
| `utm_term` | Keyword term |
| `utm_content` | Creative identifier |
| `deep_link` | Target path (push payload) |
| `campaign_id` | Push campaign ID |

### Attribution Sources

1. **Cold start** — `Intent.data` from launcher (NavHostFragment handles navigation automatically; app only extracts UTM)
2. **Warm start** — `onNewIntent()` → manual navigation via `NavDeepLinkRequest` + `NavOptions`
3. **Push tap** — `Intent` extras (`campaign_id`, `deep_link`, `utm_*`) → fires `PushTapped` + `CampaignOpened`
4. **AppsFlyer OneLink** — `AppsFlyerTracker.onDeepLink` callback → fires `CampaignOpened`, routes to destination

All deep links use `popUpTo(startDestination, inclusive=false)` + `launchSingleTop=true` to prevent back-stack accumulation. Falls back to home on unrecognized URI.

---

## Session Replay

Session replay is active across 4 SDKs with coordinated sampling:

| SDK | Sample Rate | Mode | maskView() API |
|---|---|---|---|
| Amplitude | 40% | Full fidelity | `SessionReplayPlugin.mask(view)` |
| Mixpanel | 40% | Full fidelity | `SensitiveViewManager.INSTANCE.addSensitiveView(view)` |
| PostHog | 40% | Screenshot (full fidelity) | Appends `ph-no-capture` to `contentDescription` |
| Clarity | 100% | Full fidelity (heatmaps) | `Clarity.maskView(view)` |

**Amplitude-specific:**
- `SessionReplayPlugin(sampleRate=1.0)` added lazily on first event (after `sessionId > 0`)
- Software layer fix: All `ImageView`s forced to `LAYER_TYPE_SOFTWARE` to prevent rendering corruption in replay
- Session ID forwarded to Clarity as `amplitude_session_id` custom tag

**Uniform masking call:**
```kotlin
analyticsManager.maskView(binding.tvUserEmail)   // masks in Amplitude + Mixpanel + PostHog + Clarity
```

**PII masked by default:**
- All `EditText` / `TextInputEditText` (Mixpanel + PostHog config)
- Views tagged `android:tag="ph-no-capture"` in XML (PostHog)
- Views passed to `maskView()` in code (avatar, username, email, order ID, etc.)

---

## Push Notifications

### FCM (Firebase Cloud Messaging)

- `MarketFirebaseMessagingService` handles incoming messages
- Token broadcast to all SDKs via `analyticsManager.onNewPushToken(token)`
- Push tap attribution: data payload keys → `campaign_id`, `deep_link`, `utm_*`

**Required FCM data payload keys for attribution:**
```json
{
  "campaign_id": "campaign-uuid",
  "deep_link": "marketapp://product/123",
  "utm_source": "braze",
  "utm_medium": "push",
  "utm_campaign": "winback_q1"
}
```

### SDK Push Support

| SDK | Capability |
|---|---|
| Braze | Rich push, push stories, push primer |
| OneSignal | Standard push, in-app messages |
| Firebase | Baseline FCM delivery |
| AppsFlyer | Uninstall detection via silent push |

---

## Consent Management

### Consent Flow

1. Shown once on first launch (non-cancellable `ConsentBottomSheet`)
2. Two controls: **Analytics** (default ON) and **Notifications** (default OFF)
3. On continue: preferences saved, `setAnalyticsConsent()` called on all SDKs
4. Android 13+: `POST_NOTIFICATIONS` runtime permission requested if notifications ON

### Per-SDK Consent Behaviour

| SDK | Consent OFF behaviour |
|---|---|
| Firebase | Denies `ANALYTICS_STORAGE`, `AD_STORAGE`, `AD_USER_DATA`, `AD_PERSONALIZATION` |
| Amplitude | `configuration.optOut = true` |
| Mixpanel | `optOutTracking()` |
| PostHog | `optOut()` |
| AppsFlyer | `forGDPRUser(false, false)`, `anonymizeUser(true)` |
| Braze | `setPushNotificationSubscriptionType(UNSUBSCRIBED)`, `setEmailNotificationSubscriptionType(UNSUBSCRIBED)` |
| OneSignal | `pushSubscription.optOut()` |
| Clarity | All tracking suspended |
| Facebook | `setAutoLogAppEventsEnabled(false)`, `setAdvertiserIDCollectionEnabled(false)` |

### Braze Subscription Groups

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
val data = mutableMapOf<String, Any>(
    "brazeCustomerId" to Braze.getInstance(context).deviceId
)
AmplitudeTracker.deviceId?.let  { data["AmplitudeDeviceId"]  = it }
AmplitudeTracker.sessionId
    .takeIf { it > 0 }
    ?.let { data["AmplitudeSessionId"] = it.toString() }
AppsFlyerLib.getInstance().setAdditionalData(data)
```

> `setAdditionalData` replaces all data on each call — keep all keys in a single map.

### Clarity → Firebase

Clarity session ID is forwarded to Firebase as a custom parameter via `setOnSessionStartedCallback`, enabling session replay lookup from Crashlytics.

### Amplitude → Clarity

Amplitude session ID synced to Clarity via `Clarity.setCustomTag("amplitude_session_id", ...)` — updated on SDK init and on each user identify.

---

## Home Screen Architecture

```
RecyclerView (vertical, full recycling)
│
├─ PromotionsHeaderAdapter  (position 0, conditionally shown)
│       └─ RecyclerView (horizontal, promotions carousel)
│
└─ ProductAdapter           (positions 1..N)
```

Implemented with `ConcatAdapter` — replaces the legacy `NestedScrollView + nestedScrollingEnabled=false` pattern that prevented RecyclerView recycling and caused jank on large product lists.

---

## Debug QA Panel (DEBUG builds only)

Visible in **Profile → Debug Card**:

| Button | Action |
|---|---|
| Test Push | Fires `trigger_for_pushnotif` → Braze sends test push |
| Test Banner | Fires `trigger_for_banner` → Braze shows banner campaign |
| Test In-App | Fires `trigger_for_inapp` → Braze shows in-app message |
| Test Content Card | Fires `trigger_for_content_card` + opens `ContentCardsBottomSheet` |
| Test Experiment | Fires `trigger_for_experiment` → Statsig experiment preview |

All `trigger_*` events call `braze.requestImmediateDataFlush()` to ensure the campaign triggers without the normal batch-flush delay.

**Debug Logcat** — all analytics logs use tag `"Analytics"` with source prefix:

| Prefix | Source |
|---|---|
| `[DeepLink]` | Deep link routing and UTM attribution — includes `launch=cold\|warm\|hot` and `source=native\|push\|appsflyer` |
| `[AF]` | AppsFlyer UID |
| `[AF/EDDL]` | AppsFlyer extended deferred deep link (fresh install) |
| `[AF/UDL]` | AppsFlyer unified deep link (app already installed) |
| `[FCM]` | Firebase Installation ID and FCM registration token |
| `[DeviceID]` | Device identifiers for AppsFlyer test device registration |
| `[Facebook]` | Facebook SDK |
| `[PostHog]` | PostHog |

**Deep link log format:**
```
[DeepLink] launch=cold  source=native    uri=marketapp://product/123 utm_source=email
[DeepLink] launch=warm  source=native    uri=marketapp://product/123 utm_source=push
[DeepLink] launch=hot   source=native    uri=marketapp://product/123 utm_source=social
[DeepLink] launch=cold  source=push      utm_source=braze campaign_id=abc deep_link=marketapp://home
[DeepLink] launch=warm  source=appsflyer utm_source=email medium=newsletter campaign=winback deep_link=marketapp://product/99
[DeepLink] routing      launch=warm      uri=marketapp://product/99
[DeepLink] no destination launch=hot     uri=marketapp://unknown — falling back to home
```

**Device identifier log format** (debug only, on app start):
```
[AF]       AppsFlyer UID: <afid>
[DeviceID] Android ID:    <android_id>         ← primary AF test device identifier
[DeviceID] IMEI:          <imei>               ← API < 29 only; requires READ_PHONE_STATE
[DeviceID] OAID:          requires MSA SDK     ← MSA SDK not integrated
[DeviceID] Google AID:    <gaid>               ← Google Advertising ID (GAID)
[DeviceID] Fire AID:      <fire_aid>           ← Amazon Fire devices only
```

Filter with: `adb logcat -s Analytics`

---

## Navigation Destinations

| Fragment | Deep Link | Bottom Nav |
|---|---|---|
| `homeFragment` | `marketapp://home` | ✓ |
| `searchFragment` | `marketapp://search` | ✓ |
| `cartFragment` | `marketapp://cart` | ✓ |
| `profileFragment` | — | ✓ |
| `productDetailFragment` | `marketapp://product/{productId}` | hidden |
| `categoryFragment` | — | hidden |
| `checkoutFragment` | — | hidden |
| `orderConfirmationFragment` | — | hidden |

---

## BuildConfig Keys

Set in `local.properties` and injected at build time:

```
MIXPANEL_TOKEN
BRAZE_API_KEY
ONESIGNAL_APP_ID
APPSFLYER_DEV_KEY
POSTHOG_API_KEY
AMPLITUDE_API_KEY
CLARITY_PROJECT_ID
FACEBOOK_CLIENT_TOKEN
SEGMENT_WRITE_KEY
STATSIG_CLIENT_KEY
AMPLITUDE_EXPERIMENT_DEPLOYMENT_KEY
ENABLE_ANALYTICS_LOGGING        (true in debug, false in release)
```

---

## Permissions

```xml
android.permission.INTERNET
android.permission.POST_NOTIFICATIONS
android.permission.RECEIVE_BOOT_COMPLETED
android.permission.ACCESS_FINE_LOCATION        <!-- Braze geofences -->
android.permission.ACCESS_BACKGROUND_LOCATION  <!-- Braze geofences API 29+ -->
com.google.android.gms.permission.AD_ID        <!-- API 31+ -->
```

---

## Key File Paths

```
app/src/main/java/com/marketapp/
├─ analytics/
│   ├─ AnalyticsManager.kt       Fan-out coordinator
│   ├─ AnalyticsTracker.kt       Tracker interface
│   ├─ AnalyticsEvent.kt         Sealed event hierarchy
│   ├─ AmplitudeTracker.kt       Amplitude + Session Replay
│   ├─ Trackers.kt               All other 10 SDK trackers
│   └─ SessionReplayLogger.kt    Sampling decision logger
├─ di/
│   └─ AnalyticsModule.kt        Hilt bindings for Set<AnalyticsTracker>
├─ data/preferences/
│   └─ AppPreferences.kt         consentShown, analyticsEnabled, notificationsEnabled
├─ ui/
│   ├─ consent/ConsentBottomSheet.kt
│   ├─ home/
│   │   ├─ HomeFragment.kt
│   │   ├─ ProductAdapter.kt
│   │   └─ PromotionsHeaderAdapter.kt
│   ├─ profile/
│   │   ├─ ProfileFragment.kt
│   │   └─ ContentCardsBottomSheet.kt
│   └─ auth/AuthViewModel.kt
└─ MainActivity.kt               Deep link routing, Braze IAM lifecycle
```