package com.marketapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.braze.push.BrazeFirebaseMessagingService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MarketFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var analyticsManager: AnalyticsManager

    /**
     * Called when a new FCM registration token is generated.
     * Fans out to all trackers (Braze, Mixpanel, etc.) via AnalyticsManager,
     * and refreshes the token in Firestore if a user is signed in.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        analyticsManager.onNewPushToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmToken", token)
    }

    /**
     * Called when a push message is received while app is in foreground.
     *
     * Braze-originated messages (including silent uninstall-tracking pings) are
     * identified by handleBrazeRemoteMessage() and processed by the Braze SDK —
     * Braze displays its own notifications, so we skip our custom display logic.
     * Non-Braze messages (from our own backend) continue to the showNotification path.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Braze uninstall-tracking: a silent FCM ping Braze sends to detect app removal.
        // It carries no user-visible content; discard immediately before any processing.
        val bundle = android.os.Bundle().apply { message.data.forEach { (k, v) -> putString(k, v) } }
        if (com.braze.models.push.BrazeNotificationPayload(bundle).isUninstallTrackingPush) return

        // Let Braze handle its own push messages (dashboard notifications + content card sync).
        if (BrazeFirebaseMessagingService.handleBrazeRemoteMessage(this, message)) return

        val data           = message.data
        val campaignId     = data["campaign_id"]
        val type           = data["type"] ?: "promotional"
        val deepLink       = data["deep_link"]
        // fcm_options.analytics_label is set server-side in the FCM HTTP v1 payload and
        // consumed automatically by Firebase Analytics for GA4 notification events.
        // For cross-tool attribution (Mixpanel, PostHog, etc.) the server should also
        // include it in the FCM data map so we can read and forward it here.
        val analyticsLabel = data["analytics_label"]

        analyticsManager.track(
            AnalyticsEvent.PushReceived(campaignId = campaignId, type = type)
        )

        // Show notification
        message.notification?.let { notif ->
            showNotification(
                title          = notif.title ?: getString(R.string.app_name),
                body           = notif.body  ?: "",
                deepLink       = deepLink,
                campaignId     = campaignId,
                analyticsLabel = analyticsLabel,
                utmSource      = data["utm_source"],
                utmMedium      = data["utm_medium"],
                utmCampaign    = data["utm_campaign"] ?: analyticsLabel,
                utmTerm        = data["utm_term"],
                utmContent     = data["utm_content"]
            )
        }
    }

    private fun showNotification(
        title: String,
        body: String,
        deepLink: String?,
        campaignId: String?,
        analyticsLabel: String?,
        utmSource: String?,
        utmMedium: String?,
        utmCampaign: String?,
        utmTerm: String?,
        utmContent: String?
    ) {
        val channelId = "market_general"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (Android 8+)
        val channel = NotificationChannel(
            channelId,
            "Market Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Promotions, order updates, and more" }
        nm.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            deepLink?.let       { putExtra("deep_link",         it) }
            campaignId?.let     { putExtra("campaign_id",       it) }
            analyticsLabel?.let { putExtra("fcm_analytics_label", it) }
            // UTM params forwarded so MainActivity can attribute to all martech platforms
            utmSource?.let      { putExtra("utm_source",        it) }
            utmMedium?.let      { putExtra("utm_medium",        it) }
            utmCampaign?.let    { putExtra("utm_campaign",      it) }
            utmTerm?.let        { putExtra("utm_term",          it) }
            utmContent?.let     { putExtra("utm_content",       it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
    }
}
