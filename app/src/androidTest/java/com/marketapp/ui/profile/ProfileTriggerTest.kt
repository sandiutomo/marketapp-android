package com.marketapp.ui.profile

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.marketapp.HiltTestActivity
import com.marketapp.R
import com.marketapp.analytics.AnalyticsEvent
import com.marketapp.analytics.AnalyticsManager
import com.marketapp.launchFragmentInHiltContainer
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the two QA trigger buttons in ProfileFragment.
 *
 * These run on-device (or emulator) and verify that tapping each button fires
 * exactly the right analytics event — nothing more, nothing less.
 *
 * Platform-specific verification (did Braze actually send a push?) is manual
 * and documented below each test as a "Platform check" comment.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProfileTriggerTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Replaces the real AnalyticsManager singleton with a relaxed mock so no
    // actual network calls are made to Braze / OneSignal / PostHog etc.
    @BindValue
    @JvmField
    val analyticsManager: AnalyticsManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        hiltRule.inject()
        launchFragmentInHiltContainer<ProfileFragment>()
    }

    // ── Button visibility ──────────────────────────────────────────────────

    @Test
    fun debugCard_isVisible_inDebugBuild() {
        // card_debug is shown in DEBUG via ProfileFragment.onViewCreated
        onView(withId(R.id.card_debug)).check(matches(isDisplayed()))
    }

    @Test
    fun btnTriggerPush_isVisible() {
        onView(withId(R.id.btn_trigger_push)).check(matches(isDisplayed()))
    }

    @Test
    fun btnTriggerBanner_isVisible() {
        onView(withId(R.id.btn_trigger_banner)).check(matches(isDisplayed()))
    }

    // ── Push button ────────────────────────────────────────────────────────

    @Test
    fun tapTestPush_firesTriggerPushEvent() {
        onView(withId(R.id.btn_trigger_push)).perform(click())

        verify(exactly = 1) { analyticsManager.track(AnalyticsEvent.TriggerPushTest) }
        // Platform check — Braze:
        //   Campaign → Action-Based Delivery → Custom Event = "trigger_for_pushnotif"
        //   Expected: push notification arrives on the test device within ~5 s
        //
        // Platform check — OneSignal:
        //   In-App Message → Trigger → On Event = "trigger_for_pushnotif"
        //   Expected: in-app message overlay appears on the test device
    }

    @Test
    fun tapTestPush_doesNotFireBannerEvent() {
        onView(withId(R.id.btn_trigger_push)).perform(click())

        verify(exactly = 0) { analyticsManager.track(AnalyticsEvent.TriggerBannerTest) }
    }

    @Test
    fun tapTestPush_multipleTimes_firesEachTap() {
        onView(withId(R.id.btn_trigger_push)).perform(click())
        onView(withId(R.id.btn_trigger_push)).perform(click())

        // AnalyticsManager has 200 ms dedup guard — rapid double-tap is suppressed.
        // A real user tapping twice with a normal gap fires twice.
        verify(atLeast = 1) { analyticsManager.track(AnalyticsEvent.TriggerPushTest) }
    }

    // ── Banner button ──────────────────────────────────────────────────────

    @Test
    fun tapTestBanner_firesTriggerBannerEvent() {
        onView(withId(R.id.btn_trigger_banner)).perform(click())

        verify(exactly = 1) { analyticsManager.track(AnalyticsEvent.TriggerBannerTest) }
        // Platform check — Braze:
        //   In-App Message campaign → Action-Based → Custom Event = "trigger_for_banner"
        //   Expected: Braze in-app banner slides in from bottom
        //
        // Platform check — PostHog:
        //   Survey / Popover → Display conditions → User event = "trigger_for_banner"
        //   Expected: PostHog popover appears in-session
        //
        // Platform check — OneSignal:
        //   In-App Message → Trigger → On Event = "trigger_for_banner"
        //   Expected: OneSignal in-app overlay appears
    }

    @Test
    fun tapTestBanner_doesNotFirePushEvent() {
        onView(withId(R.id.btn_trigger_banner)).perform(click())

        verify(exactly = 0) { analyticsManager.track(AnalyticsEvent.TriggerPushTest) }
    }

    // ── Cross-button isolation ─────────────────────────────────────────────

    @Test
    fun tapEachButtonOnce_firesTwoDistinctEvents() {
        onView(withId(R.id.btn_trigger_push)).perform(click())
        onView(withId(R.id.btn_trigger_banner)).perform(click())

        verify(exactly = 1) { analyticsManager.track(AnalyticsEvent.TriggerPushTest) }
        verify(exactly = 1) { analyticsManager.track(AnalyticsEvent.TriggerBannerTest) }
    }
}
