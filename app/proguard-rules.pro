# ── App ─────────────────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# Data Models — keep for JSON deserialization
-keep class com.marketapp.data.model.** { *; }

# ── Firebase ─────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# ── PHASE 2 — Mixpanel ───────────────────────────────────────────────────────
# -keep class com.mixpanel.** { *; }

# ── PHASE 3 — Braze ──────────────────────────────────────────────────────────
# -keep class com.braze.** { *; }
# -keep class com.appboy.** { *; }

# ── PHASE 4 — AppsFlyer ──────────────────────────────────────────────────────
# -keep class com.appsflyer.** { *; }
# -dontwarn com.appsflyer.**

# ── PHASE 5 — PostHog ────────────────────────────────────────────────────────
# -keep class com.posthog.** { *; }

# ── PHASE 6 — Amplitude ──────────────────────────────────────────────────────
# -keep class com.amplitude.** { *; }

# ── PHASE 7 — Segment ────────────────────────────────────────────────────────
# -keep class com.segment.** { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembernames interface * {
    @retrofit2.http.* <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Hilt / Dagger
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
