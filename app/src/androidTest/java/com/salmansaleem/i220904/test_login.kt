package com.salmansaleem.i220904

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import java.util.concurrent.atomic.AtomicBoolean

@LargeTest
@RunWith(AndroidJUnit4::class)
class SocialAppTests {

    @Rule
    @JvmField
    var mActivityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    // Custom IdlingResource to wait for splash screen
    private val splashScreenIdlingResource = object : IdlingResource {
        private val isIdle = AtomicBoolean(false)
        private var callback: IdlingResource.ResourceCallback? = null

        override fun getName(): String = "SplashScreenIdlingResource"

        override fun isIdleNow(): Boolean = isIdle.get()

        override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
            this.callback = callback
            // Wait for splash screen duration + buffer
            Thread {
                Thread.sleep(3000) // 2500ms from Handler + 500ms buffer
                isIdle.set(true)
                callback?.onTransitionToIdle()
            }.start()
        }
    }

    @Test
    fun testSuccessfulLogin() {
        // Register idling resource to wait for splash screen
        IdlingRegistry.getInstance().register(splashScreenIdlingResource)

        // Now on signup screen after splash, navigate to login
        onView(withId(R.id.login2))
            .perform(scrollTo(), click())

        // Enter username
        onView(withId(R.id.username))
            .perform(scrollTo(), typeText("salmansaleem08"), closeSoftKeyboard())

        // Enter password
        onView(withId(R.id.password))
            .perform(scrollTo(), typeText("123456"), closeSoftKeyboard())

        // Click login button
        onView(withId(R.id.login))
            .perform(scrollTo(), click())

        // Cleanup
        IdlingRegistry.getInstance().unregister(splashScreenIdlingResource)
    }
}