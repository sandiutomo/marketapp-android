import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
}

android {
    namespace   = "com.marketapp"
    compileSdk  = 35

    defaultConfig {
        applicationId             = "com.marketapp"
        minSdk                    = 26
        targetSdk                 = 35
        versionCode               = 1
        versionName               = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ── MARTECH KEYS (override via local.properties or CI secrets) ──────
        // Firebase reads from google-services.json — no extra config needed.
        buildConfigField("String", "MIXPANEL_TOKEN",           "\"${localProp("MIXPANEL_TOKEN")}\"")
        buildConfigField("String", "BRAZE_API_KEY",          "\"${localProp("BRAZE_API_KEY")}\"")
        buildConfigField("String", "ONESIGNAL_APP_ID",       "\"${localProp("ONESIGNAL_APP_ID")}\"")
        buildConfigField("String", "APPSFLYER_DEV_KEY",        "\"${localProp("APPSFLYER_DEV_KEY")}\"")
        buildConfigField("String", "POSTHOG_API_KEY",           "\"${localProp("POSTHOG_API_KEY")}\"")
        buildConfigField("String", "AMPLITUDE_API_KEY",        "\"${localProp("AMPLITUDE_API_KEY")}\"")
        buildConfigField("String", "CLARITY_PROJECT_ID",        "\"${localProp("CLARITY_PROJECT_ID")}\"")
        buildConfigField("String", "FACEBOOK_CLIENT_TOKEN",     "\"${localProp("FACEBOOK_CLIENT_TOKEN")}\"")
        buildConfigField("String", "SEGMENT_WRITE_KEY",         "\"${localProp("SEGMENT_WRITE_KEY")}\"")
        buildConfigField("String", "STATSIG_CLIENT_KEY",              "\"${localProp("STATSIG_CLIENT_KEY")}\"")
        buildConfigField("String", "AMPLITUDE_EXPERIMENT_DEPLOYMENT_KEY", "\"${localProp("AMPLITUDE_EXPERIMENT_DEPLOYMENT_KEY")}\"")
        // Inject Client Token into manifest so FacebookInitProvider can fully initialize
        // before our manual FacebookTracker.initialize() runs on the IO thread.
        manifestPlaceholders["facebook_client_token"] = localProp("FACEBOOK_CLIENT_TOKEN")
        // ────────────────────────────────────────────────────────────────────
    }

    buildTypes {
        debug {
            isDebuggable         = true
            isMinifyEnabled      = false
            versionNameSuffix    = "-debug"
            buildConfigField("Boolean", "ENABLE_ANALYTICS_LOGGING", "true")
        }
        release {
            isMinifyEnabled      = true
            isShrinkResources    = true
            isDebuggable         = false
            buildConfigField("Boolean", "ENABLE_ANALYTICS_LOGGING", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // Replace with release signing
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(listOf("-opt-in=kotlin.RequiresOptIn"))
    }
}

// local.properties helper ──────────────────────────────────────────────────
fun localProp(key: String): String {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) props.load(file.inputStream())
    return props.getProperty(key, "")
}

dependencies {
    // ── AndroidX Core ────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefresh)
    implementation(libs.androidx.splashscreen)

    // ── Navigation ───────────────────────────────────────────────────────────
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // ── Lifecycle ────────────────────────────────────────────────────────────
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.livedata)

    // ── Async ────────────────────────────────────────────────────────────────
    implementation(libs.coroutines.android)

    // ── Network ──────────────────────────────────────────────────────────────
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // ── Image ────────────────────────────────────────────────────────────────
    implementation(libs.coil)

    // ── DI ───────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // ── Storage ──────────────────────────────────────────────────────────────
    implementation(libs.datastore.prefs)

    // ── MARTECH ──────────────────────────────────────────────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.config)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.performance)
    implementation(libs.firebase.inappmessaging)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.appset)
    implementation(libs.play.services.ads.identifier)

    implementation(libs.mixpanel)
    implementation(libs.mixpanel.session.replay)

    implementation(libs.appsflyer)
    implementation(libs.appsflyer.install.referrer)

    implementation(libs.braze.android.sdk)
    implementation(libs.onesignal)

    implementation(libs.amplitude)
    implementation(libs.amplitude.experiment)
    implementation(libs.amplitude.session.replay) {
        // analytics-android bundles analytics-core internally; exclude the standalone
        // analytics-core transitive dep to avoid duplicate class errors at build time.
        exclude(group = "com.amplitude", module = "analytics-core")
    }

    implementation(libs.posthog)

    implementation(libs.segment)
    implementation(libs.statsig)

    implementation(libs.clarity)

    implementation(libs.facebook.core)

    // ── Test ─────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.hilt.testing)
    kaptAndroidTest(libs.hilt.compiler)
}
