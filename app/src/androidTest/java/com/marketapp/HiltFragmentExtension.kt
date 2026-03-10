package com.marketapp

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

/**
 * Launches a Fragment inside [HiltTestActivity] so Hilt injection works.
 * Use in @HiltAndroidTest classes instead of launchFragmentInContainer().
 */
inline fun <reified T : Fragment> launchFragmentInHiltContainer(
    fragmentArgs: Bundle? = null,
    @StyleRes themeResId: Int = R.style.Theme_MarketApp,
) {
    val intent = Intent.makeMainActivity(
        ComponentName(ApplicationProvider.getApplicationContext(), HiltTestActivity::class.java)
    ).putExtra(
        "androidx.fragment.app.testing.FragmentScenario.EmptyFragmentActivity.THEME_EXTRAS_BUNDLE_KEY",
        themeResId
    )

    ActivityScenario.launch<HiltTestActivity>(intent).onActivity { activity ->
        val fragment = activity.supportFragmentManager.fragmentFactory
            .instantiate(T::class.java.classLoader!!, T::class.java.name)
            .apply { arguments = fragmentArgs }

        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "")
            .commitNow()
    }
}
