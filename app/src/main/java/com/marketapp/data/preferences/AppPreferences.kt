package com.marketapp.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists app-level user preferences via SharedPreferences.
 *
 * Currently tracks:
 *  - Whether the consent/permission prompt has been shown (so it only appears once).
 *  - The user's choice for push notifications and analytics collection.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True once the consent bottom sheet has been dismissed with "Continue". */
    var consentShown: Boolean
        get() = prefs.getBoolean(KEY_CONSENT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_CONSENT_SHOWN, value).apply()

    /** Whether the user opted in to push notifications. */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    /** Whether the user opted in to analytics and personalization. */
    var analyticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANALYTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_ANALYTICS, value).apply()

    companion object {
        private const val PREFS_NAME      = "app_preferences"
        private const val KEY_CONSENT_SHOWN = "consent_shown"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_ANALYTICS     = "analytics_enabled"
    }
}