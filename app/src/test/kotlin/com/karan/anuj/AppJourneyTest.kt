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
import com.karan.anuj.feature.reminder.platform.ReminderRunner
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

    @Inject
    lateinit var reminders: ReminderRunner

    @Before
    fun start() {
        hilt.inject()
        /** The real Application does these on start; the test application is a stand-in that does not. */
        appLock.attach()
        reminders.attach()
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

    /** For a word that is also part of other labels: "Ask" is inside "Ask what to do after". */
    private fun tapExactText(text: String) {
        val tappable = hasText(text, substring = false) and hasClickAction()
        waitOrExplain("something tappable saying exactly \"$text\"") { app.onAllNodes(tappable).fetchSemanticsNodes().isNotEmpty() }
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

        // The add button asks what is being added, then takes a name.
        tapLabel("Add")
        waitForText("What are you adding?")
        tapExactText("Task")
        waitForText("New task")
        type("Buy milk")
        tapText("Add task")
        /** The name is also in the sheet's text box, so the sheet must be gone before the row can be told apart from it. */
        waitForNoText("New task")
        waitForText("Buy milk")

        // A routine is asked how often it comes round instead of which day, and says what it is in the list.
        tapLabel("Add")
        tapExactText("Routine")
        waitForText("New routine")
        type("Stretch")
        tapText("Add routine")
        waitForNoText("New routine")
        waitForText("Routine · Today · Every day")

        // A task opens with a few rows; the rest is under "More", which says what is hidden there.
        tapText("Buy milk")
        waitForText("Remind me")
        waitForText("Description, priority, tags, notes, photos")
        waitForNoText("If not done on its day")
        waitForNoText("Notes")
        waitForNoText("Photos")

        // A step is typed straight onto the task and is a task of its own.
        type("Take a bag")
        tapLabel("Add")
        waitForText("Take a bag")

        // A reminder is set by tapping a chip; the repeat and style only appear once there is one.
        tapText("Remind me")
        tapText("At the time")
        waitForText("Keep reminding until done")
        waitForText("Full-screen alarm")
        tapText("OK")
        waitForNoText("Keep reminding until done")
        waitForText("At the time")

        // Priority is under "More"; once set, the closed "More" line names it.
        tapText("More")
        waitForText("If not done on its day")
        tapText("High")
        tapLabel("Back")
        waitForText("Today · 0 of 1 steps · High")

        // The tree: a routine expands to show its steps.
        tapText("Tasks")
        tapLabel("Show steps of Morning routine")
        waitForText("Brush teeth")

        // A routine's steps are played one at a time.
        tapText("Morning routine")
        waitForText("Do the steps one by one")
        tapText("Do the steps one by one")
        waitForText("Step 1 of 5")
        waitForText("Drink a glass of water")
        tapText("Done, next")
        waitForText("Step 2 of 5")
        tapText("Not now")
        waitForText("Brush teeth")
        tapLabel("Back")
        waitForText("Do the steps one by one")
        tapLabel("Back")

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
        waitForText("Remind me")
        tapText("More")
        tapText("Move to trash")
        waitForNoText("Buy milk")
        tapLabel("More")
        tapText("Trash")
        waitForText("Buy milk")
        tapText("Restore")
        waitForText("Trash is empty")
        tapLabel("Back")
        waitForText("Buy milk")

        // Settings is opened from the home menu and is a tree: the first screen only names its branches.
        tapExactText("Today")
        tapLabel("Menu")
        tapText("Settings")
        waitForText("Theme, colours, text size, which hand")
        waitForNoText("Backup folder")

        // Display: the second set of colours and the hand the screens are laid out for.
        tapText("Theme, colours, text size, which hand")
        waitForText("Right hand")
        waitForText("Start lists lower")
        tapText("Colours")
        tapText("Ivory")
        tapLabel("Back")
        tapText("Theme, colours, text size, which hand")
        waitForText("Ivory")
        tapLabel("Back")

        // Tasks: values that used to be fixed in code are settings.
        tapText("Unfinished tasks, times of day, lengths")
        waitForText("Unfinished tasks")
        waitForText("Times of day")
        waitForText("8:00 am · 1:00 pm · 6:00 pm · 9:00 pm")
        tapLabel("Back")

        tapText("Folder, schedule, password, restore")
        waitForText("Backup folder")
        waitForText("Backups kept")
        tapLabel("Back")

        // Reminder settings: the daily limit is changed by a chip, and shows on its row.
        tapText("Calm mode, notifications, regular reminders, a check")
        waitForText("Calm mode")
        tapText("Notifications")
        waitForText("Quiet hours")
        waitForText("Kinds of reminder")
        waitForText("Up to 30 a day")
        tapText("Daily limit")
        tapText("No limit")
        tapText("OK")
        waitForNoText("Up to 30 a day")
        tapLabel("Back")

        // A regular reminder is added from a ready-made chip, with nothing typed.
        tapText("Regular reminders")
        waitForText("Nothing here yet")
        tapText("Water")
        waitForText("Every so often")
        tapText("OK")
        waitForNoText("Every so often")
        waitForText("Every 2 h, 9:00 am to 9:00 pm")
        tapLabel("Back")

        // The reminder check lists what the phone must allow and offers a test.
        tapText("Reminder check")
        waitForText("Notifications are allowed")
        waitForText("Send a test reminder")
        tapLabel("Back")
        tapLabel("Back")

        // Places: off until switched on, and every number the rules use is a setting.
        tapText("Home, saved places, the leaving list, new spots")
        waitForText("Notice places")
        waitForText("Works without internet, Wi-Fi or Bluetooth")
        waitForText("100 metres around it")
        waitForText("Ask after one stay of")
        waitForNoText("Check where I am now")
        tapLabel("Back")

        // Voice settings: the name, when it listens, and how many tasks it reads.
        tapText("The assistant's name, when it listens")
        waitForText("Teach it its name")
        waitForText("Things you can say")
        tapText("Tasks read out before")
        tapText("8")
        tapText("OK")
        waitForNoText("OK")
        tapLabel("Back")

        // History and storage: nothing is thrown away unless asked for, and the logs can be read.
        tapText("The trash, photos of deleted tasks, the logs")
        waitForText("Never")
        waitForText("Deleted with the task")
        waitForText("Always")
        tapText("What was edited, and every reminder")
        waitForText("Edits")
        waitForText("Priority: NONE → HIGH")
        tapLabel("Back")
        tapLabel("Back")

        // App lock: the delay is a chip.
        tapText("Fingerprint or screen lock")
        tapText("Lock again after")
        tapText("5 min away")
        waitForText("Straight away")
        tapLabel("Back")
        tapLabel("Back")

        // The assistant by tap: a shortcut asks the same question a voice would.
        tapExactText("Ask")
        waitForText("Tap to talk")
        tapText("Tell me the list of tasks")
        waitForText("You said: Tell me the list of tasks")
    }

    private companion object {
        const val TIMEOUT = 15_000L
    }
}
