package com.marketapp.data.repository

import android.content.Context
import android.provider.Settings
import com.google.android.gms.appset.AppSet
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.tasks.Tasks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects device-level identifiers used by analytics platforms for cross-device
 * attribution, deduplication, and experimentation.
 *
 *  - deviceId      — android.provider.Settings.Secure.ANDROID_ID
 *                    Stable within an install; resets only on factory reset or re-install.
 *  - appSetId      — Google App Set ID (per-developer scope).
 *                    Privacy-friendly alternative to advertising ID; does not require consent.
 *  - advertisingId — Google Advertising ID. Null when user enables "Opt out of Ads
 *                    Personalization" (Limit Ad Tracking). Requires AD_ID permission on API 31+.
 */
@Singleton
class DeviceInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Synchronous — available immediately without any async fetch. */
    val deviceId: String = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ANDROID_ID
    ) ?: ""

    private val mutex               = Mutex()
    private var fetched             = false
    private var _appSetId: String?      = null
    private var _advertisingId: String? = null

    val appSetId: String?      get() = _appSetId
    val advertisingId: String? get() = _advertisingId

    /**
     * Fetches AppSet ID and Advertising ID on the IO dispatcher and caches them.
     * Idempotent — safe to call from multiple coroutines; only the first caller fetches.
     * Times out after 3 seconds per ID so it never blocks auth-state processing indefinitely.
     */
    suspend fun fetchOnce() = mutex.withLock {
        if (fetched) return@withLock
        withContext(Dispatchers.IO) {
            runCatching {
                val info = Tasks.await(AppSet.getClient(context).appSetIdInfo, 3, TimeUnit.SECONDS)
                _appSetId = info.id
            }
            runCatching {
                withTimeout(3_000L) {
                    val adInfo = runInterruptible { AdvertisingIdClient.getAdvertisingIdInfo(context) }
                    _advertisingId = if (!adInfo.isLimitAdTrackingEnabled) adInfo.id else null
                }
            }
        }
        fetched = true
    }
}
