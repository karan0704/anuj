package com.karan.anuj

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.room.Room
import com.karan.anuj.core.data.db.AnujDatabase
import com.karan.anuj.core.data.di.DatabaseModule
import com.karan.anuj.core.security.AppLockController
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Inject
import javax.inject.Singleton
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The encrypted database needs a phone, so the test swaps in an unencrypted
 * in-memory one. Everything else — every repository, use case, view model
 * and screen — is exactly what the app runs.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object InMemoryDatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AnujDatabase =
        Room.inMemoryDatabaseBuilder(context, AnujDatabase::class.java).allowMainThreadQueries().build()
}

/**
 * Starts the real app on the computer and uses it the way a person would,
 * from first run through the main task flows. It is one long journey on
 * purpose: each step needs what the step before it left behind, and the
 * settings store is shared for the whole test run.
 *
 * The screen is made very tall so every row of a list is on screen at once
 * and the test never has to scroll to find one.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class AppJourneyTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val app = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var appLock: AppLockController

    @Before
    fun start() {
        hilt.inject()
        /** The real Application does this on start; the test application is a stand-in that does not. */
        appLock.attach()
    }

    /**
     * Matches part of a node's text, because a task row is read out as one
     * line ("Buy milk, Today · High") rather than as separate pieces.
     */
    private fun textShown(text: String) = app.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun labelShown(label: String) = app.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) = waitOrExplain("\"$text\" to appear") { textShown(text) }

    private fun waitForNoText(text: String) = waitOrExplain("\"$text\" to go away") { !textShown(text) }

    /** On a timeout, fails with everything that is on screen, so the report shows where the journey actually was. */
    private fun waitOrExplain(what: String, condition: () -> Boolean) {
        try {
            app.waitUntil(TIMEOUT, condition)
        } catch (timeout: ComposeTimeoutException) {
            val roots = app.onAllNodes(isRoot()).fetchSemanticsNodes().size
            val screen = (0 until roots).joinToString("\n--- next window ---\n") { index ->
                app.onAllNodes(isRoot())[index].printToString(maxDepth = 40)
            }
            throw AssertionError("Timed out waiting for $what. On screen:\n$screen", timeout)
        }
    }

    /**
     * Taps are sent as the control's own click action rather than as a touch
     * at a screen position. The outcome is the same code running, and it does
     * not depend on where Robolectric places a bottom sheet's window.
     */
    private fun tapText(text: String) {
        val tappable = hasText(text, substring = true) and hasClickAction()
        waitOrExplain("something tappable saying \"$text\"") { app.onAllNodes(tappable).fetchSemanticsNodes().isNotEmpty() }
        app.onAllNodes(tappable).onFirst().performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun tapLabel(label: String) {
        waitOrExplain("the control labelled \"$label\"") { labelShown(label) }
        app.onAllNodesWithContentDescription(label).onFirst().performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun type(text: String) {
        app.waitUntil(TIMEOUT) { app.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        app.onAllNodes(hasSetTextAction()).onFirst().performTextInput(text)
    }

    @Test
    fun `first run to working task list`() {
        // First run: welcome, notifications, app lock, routines.
        tapText("Get started")
        waitForText("Let me remind you")
        tapText("Not now")
        waitForText("Keep it private")
        /** The lock page offers "Continue" on a device with no screen lock, "Not now" on one that has it. */
        app.waitUntil(TIMEOUT) { textShown("Continue") || textShown("Not now") }
        tapText(if (textShown("Continue")) "Continue" else "Not now")
        waitForText("Start with some routines?")
        tapText("Add selected")

        // Home shows the routines, due today.
        waitForText("Morning routine")
        waitForText("Leaving home")
        waitForText("Drink water")
        waitForText("Go to bed on time")

        // Ticking a repeating task finishes today's round; Undo brings it back.
        tapLabel("Mark Drink water as done")
        waitForText("Done today (1)")
        waitForText("Done: Drink water")
        tapText("Undo")
        waitForNoText("Done today (1)")

        // Quick add: two taps and a name.
        tapLabel("Add a task")
        waitForText("New task")
        type("Buy milk")
        tapText("Add task")
        /** The name is also in the sheet's text box, so the sheet must be gone before the row can be told apart from it. */
        waitForNoText("New task")
        waitForText("Buy milk")

        // Open the task and set its priority by tapping a chip.
        tapText("Buy milk")
        waitForText("If not done on its day")
        tapText("High")
        tapLabel("Back")
        waitForText("Today · High")

        // The tree: a routine expands to show its steps.
        tapText("Tasks")
        tapLabel("Show steps of Morning routine")
        waitForText("Brush teeth")

        // Inbox: a thought is caught without leaving the screen.
        tapText("Inbox")
        waitForText("Inbox is empty")
        type("Call dentist")
        tapLabel("Save thought")
        waitForText("Call dentist")
        waitForNoText("Inbox is empty")

        // Search finds it from the start of a word.
        tapText("Tasks")
        tapLabel("Search")
        type("dent")
        waitForText("Call dentist")
        tapLabel("Back")

        // Trash: delete from the task screen, find it in the trash, restore it.
        tapText("Buy milk")
        waitForText("If not done on its day")
        tapLabel("More")
        tapText("Move to trash")
        waitForNoText("Buy milk")
        tapLabel("More")
        tapText("Trash")
        waitForText("Buy milk")
        tapText("Restore")
        waitForText("Trash is empty")
        tapLabel("Back")
        waitForText("Buy milk")

        // Settings carries the task and backup rows.
        tapText("Settings")
        waitForText("Unfinished tasks")
        waitForText("Backup folder")
    }

    private companion object {
        const val TIMEOUT = 15_000L
    }
}
