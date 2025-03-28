package com.salmansaleem.i220904

import android.view.View
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
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import java.util.concurrent.atomic.AtomicBoolean
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before

@LargeTest
@RunWith(AndroidJUnit4::class)
class SocialAppTests {

    @Rule
    @JvmField
    var mActivityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    // Idling Resources
    private val splashScreenIdlingResource = createIdlingResource("SplashScreenIdlingResource", 3000)
    private val loginIdlingResource = createIdlingResource("LoginIdlingResource", 10000)
    private val navigationIdlingResource = createIdlingResource("NavigationIdlingResource", 5000)

    @Before
    fun setup() {
        // Register IdlingResources
        IdlingRegistry.getInstance().register(
            splashScreenIdlingResource,
            loginIdlingResource,
            navigationIdlingResource
        )
    }

    @After
    fun tearDown() {
        // Unregister IdlingResources
        IdlingRegistry.getInstance().unregister(
            splashScreenIdlingResource,
            loginIdlingResource,
            navigationIdlingResource
        )
    }

    // Generic IdlingResource creator
    private fun createIdlingResource(name: String, timeoutMs: Long): IdlingResource {
        return object : IdlingResource {
            private val isIdle = AtomicBoolean(false)
            private var callback: IdlingResource.ResourceCallback? = null

            override fun getName(): String = name
            override fun isIdleNow(): Boolean = isIdle.get()
            override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
                this.callback = callback
                Thread {
                    Thread.sleep(timeoutMs)
                    isIdle.set(true)
                    callback?.onTransitionToIdle()
                }.start()
            }
        }
    }

    // Custom ViewAction to click on a specific position in a RecyclerView
    private fun clickItemAtPosition(position: Int): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = Matchers.instanceOf(RecyclerView::class.java)
            override fun getDescription(): String = "Click on item at position $position"
            override fun perform(uiController: UiController, view: View) {
                val recyclerView = view as RecyclerView
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
                viewHolder?.itemView?.performClick()
                    ?: throw IllegalStateException("Item at position $position not found")
            }
        }
    }
    private fun checkmesage(){

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




    @Test
    fun testMessageSending() {
        onView(withId(R.id.login2))
            .perform(scrollTo(), click())


        onView(withId(R.id.username))
            .perform(scrollTo(), typeText("salmansaleem08"), closeSoftKeyboard())
        onView(withId(R.id.password))
            .perform(scrollTo(), typeText("123456"), closeSoftKeyboard())

        onView(withId(R.id.login)).perform(scrollTo(), click())


    }

    @Test
    fun testInviteSending(){

        onView(withId(R.id.login2))
            .perform(scrollTo(), click())


        onView(withId(R.id.username))
            .perform(scrollTo(), typeText("salmansaleem08"), closeSoftKeyboard())
        onView(withId(R.id.password))
            .perform(scrollTo(), typeText("123456"), closeSoftKeyboard())

        onView(withId(R.id.login)).perform(scrollTo(), click())
    }

}