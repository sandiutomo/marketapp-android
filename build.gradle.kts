// Top-level build file
plugins {
    alias(libs.plugins.android.application)     apply false
    alias(libs.plugins.kotlin.android)          apply false
    alias(libs.plugins.kotlin.parcelize)        apply false
    alias(libs.plugins.kotlin.kapt)             apply false
    alias(libs.plugins.hilt)                    apply false
    alias(libs.plugins.navigation.safeargs)     apply false
    alias(libs.plugins.google.services)         apply false
    alias(libs.plugins.firebase.crashlytics)    apply false
    alias(libs.plugins.firebase.perf)           apply false
}
