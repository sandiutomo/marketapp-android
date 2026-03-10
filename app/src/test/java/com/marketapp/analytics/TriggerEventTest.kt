package com.marketapp.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the QA trigger events used by the profile debug panel.
 *
 * These verify event name contracts so a rename or refactor never silently
 * breaks the Braze / OneSignal / PostHog campaign trigger configuration.
 */
class TriggerEventTest {

    // ── Event name contracts ───────────────────────────────────────────────

    @Test
    fun triggerPushTest_hasCorrectEventName() {
        assertEquals(
            "Event name must match the Braze/OneSignal campaign trigger key",
            "trigger_for_pushnotif",
            AnalyticsEvent.TriggerPushTest.name
        )
    }

    @Test
    fun triggerBannerTest_hasCorrectEventName() {
        assertEquals(
            "Event name must match the PostHog/Braze in-app trigger key",
            "trigger_for_banner",
            AnalyticsEvent.TriggerBannerTest.name
        )
    }

    @Test
    fun pushAndBanner_haveDistinctNames() {
        assertNotEquals(
            "Push and banner events must not share the same name",
            AnalyticsEvent.TriggerPushTest.name,
            AnalyticsEvent.TriggerBannerTest.name
        )
    }

    // ── Properties ────────────────────────────────────────────────────────

    @Test
    fun triggerPushTest_toPropertiesIsEmpty() {
        assertTrue(
            "trigger_for_pushnotif must carry no properties — platforms match on event name only",
            AnalyticsEvent.TriggerPushTest.toProperties().isEmpty()
        )
    }

    @Test
    fun triggerBannerTest_toPropertiesIsEmpty() {
        assertTrue(
            "trigger_for_banner must carry no properties",
            AnalyticsEvent.TriggerBannerTest.toProperties().isEmpty()
        )
    }

    // ── Singleton identity ────────────────────────────────────────────────

    @Test
    fun triggerPushTest_isSameInstance() {
        // Objects are singletons — same reference every time, no allocation cost.
        assert(AnalyticsEvent.TriggerPushTest === AnalyticsEvent.TriggerPushTest)
    }
}
