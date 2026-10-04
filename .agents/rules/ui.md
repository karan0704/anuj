# Trigger: building or changing any screen, notification or voice command

## Goal
The app must be handy: usable one-handed, by taps and by voice, with the user writing as little as possible. The user has ADHD — every extra field, screen or decision is a chance to abandon the action.

## Minimal Typing
* **Typing is never required.** The only free-text a task needs is its name, and that can be dictated. Every other field is chosen, not written.
* **Microphone on every text field** — name, description, note, journal entry. Speaking fills the field; the recording is kept.
* **Pick, don't type**:
  - Dates: chips `Today` `Tomorrow` `Next week` `Next month`, then a calendar picker.
  - Times: chips `Morning` `Afternoon` `Evening` `Night` (user-set clock times), then a dial.
  - Repetition: presets `Daily` `Weekdays` `Weekly` `Monthly` `Yearly`, then custom.
  - Days off: tap the weekday letters.
  - Numbers (weight, BP, temperature, hours): stepper or dial pre-filled with the last value.
  - Counters (water): a single `+1` button.
  - Scales (energy, mood): five tappable faces/icons.
* **Smart defaults**: a new task is due today with the app-default carry-over rule and the last-used reminder style. Saving with nothing but a name must work.
* **Reuse over re-entry**: templates, recent tasks, "duplicate", and routines so the same thing is never entered twice.

## Tap Budget
* Add a task, mark a task done, or log a tracker entry: **two taps or fewer** from the home screen.
* Anything else: three taps or fewer, or it needs a shortcut.
* Notifications carry their own actions — `Done`, `Snooze`, `Carry over` — so the app need not be opened.
* Home-screen widget and quick-settings tile for capture without opening the app.

## Tap + Voice Parity
* Every action reachable by tap must have a voice command, and every voice command must have a tap path. A feature with only one of the two is incomplete.
* Voice replies are short and spoken aloud; the screen shows the same result.
* A misheard command must be undoable by one tap or by saying "undo".

## One-Hand Layout
* Primary actions in the bottom half of the screen; nothing essential in the top corners.
* Touch targets at least 56dp.
* Bottom sheets instead of full-screen forms; optional fields collapsed under "More" (`MoreRow`), whose line says what is already set there.
* A screen opens with few controls. Chips and buttons that are not needed every time go into a sheet or under "More".
* A task, a routine and a reminder must be told apart at a glance: the add button asks which is being added, and a routine carries the repeat mark and the word "Routine" in every list.
* Swipe right = done, swipe left = snooze / carry over.
* Undo snackbar instead of "Are you sure?" dialogs, except for irreversible actions.

## Thumb-First Scrolling (asked for on 2026-10-04 — NOT BUILT YET)

This is the developer's request after using the first one-hand layout on his phone. It replaces part of what is built. **Nothing in this section is in the code yet; it is the next UI work.** The developer's own words are quoted so they can be checked against any reading of them.

**What is built today, and what he found wrong with it**
* `OneHandList` (`core/ui/components/OneHand.kt`) is used on Today, Tasks and Inbox only. The top (the clock on Today, the title on Tasks and Inbox) is fixed; the list starts about a fifth of the way down the screen; search and the menu ride at its head.
* "the list looks a quad without a bar or a title": the lowered list reads as a loose block. It has no bar of its own, and the gap above it looks empty.
* "title stays static which is not looking good": the fixed title and the fixed clock look wrong while the list moves under them.
* Every other screen (Settings and its branches, Regular reminders, the task screen, Search, Trash, History, Places, Voice) is an ordinary top-to-bottom screen. On those, the first rows are at the top of the screen and the thumb cannot reach them.

**What he asked for**
1. **A list starts from the bottom.** "it should be start from the bottom ... and then it should get scrolled to up or bottom". Content begins at the bottom of the screen, where the thumb is, and can be scrolled both ways.
2. **Every screen can be scrolled so that any row comes to the thumb**, including its first row. "this setting screen shows all the settings above if I want to select any settings from the thumb I can't select the top settings". This applies to every screen, named by him: Today, Tasks, Inbox, the Settings root, every Settings branch, Regular reminders, the task screen, Search, and menus that open from a button.
3. **The title moves with the content.** "when I am scrolling the list below or above it should dynamically move the title also". No title stays fixed while its list scrolls.
4. **The date and time on Today scroll too.** "the date and time stays above in the left side corner which I want should be scrollable also". This reverses his earlier request that the time stay in place.
5. **The list has a bar.** "there should be a bar because right now the list looks a quad without a bar or a title". The list needs a visible bar at its head that travels with it.
6. **A scroll bar everywhere.** "scroll bar for everything and everywhere". Every scrolling screen shows where it is and how much there is.
7. **Right or left thumb.** "their first job is to make our device hands free ... supportable for the thumb right or left". The existing Hand setting (Settings, Display) keeps deciding the side.

**How to read it (the assistant's interpretation, to confirm with him before or while building)**
* One shared container for *every* screen, replacing both `OneHandList` and the plain scrolling columns: the title (or the clock) is the first item of the scrolling content, followed by the bar of actions, then the rows. Nothing is pinned at the top.
* "Start from the bottom" is read as: when a screen opens, its content is laid against the bottom edge, so a short screen (the Settings root with seven rows) sits in the lower half, and a long one opens showing its beginning with the thumb on it. The screen must then scroll far enough in both directions that the first and the last row can each be brought to thumb height, which means empty space the height of the reach area before the first row and after the last.
* "A bar" is read as one row at the head of the content holding the title and the screen's actions (back, search, menu) on the side of the chosen hand, with a visible edge, so the content reads as one sheet with a handle and not a loose block. It travels with the content.
* "A scroll bar" is read as a thin position indicator down one edge of every scrolling screen. Which edge, and whether it can be dragged, are open questions below.
* Sheets (`AnujBottomSheet`) already open at the bottom and are within reach; they need the scroll indicator only when their content is taller than the sheet.
* Dropdown menus opened from a button ("Opening the search menu in task or whatever menu, it is also not same") open at the button today. Read as: a menu should open as a bottom sheet or at thumb height, never at the top of the screen.

**Open questions for the developer**
1. Should a short screen rest at the bottom when it opens (rows in the lower half, empty space above), or open at the top and only be pullable down?
2. Scroll bar: only shows the position, or can be dragged with the thumb? On the hand's side or the opposite edge?
3. The back arrow on pushed screens: part of the moving bar, or is the phone's own back gesture enough?
4. Does the bottom tab bar stay fixed? (Assumed yes: it is already at the thumb.)
5. The "Start lists lower" setting: keep it as the switch for this whole behaviour, or remove it once every screen works this way?

**When building it**
* Build it once in `core:ui` and move every screen onto it; do not fix screens one by one with their own padding.
* The end-to-end test (`app/src/test/.../AppJourneyTest.kt`) runs on a very tall screen so that everything is composed; keep it passing, and keep the rule that the keyboard never covers the focused field.
* Remove `OneHandList` and the fixed-top code it replaces in the same change (old code is removed, not kept).

## Layout That Stays Aligned (the Instagram / Discord feel)
* **Every full screen sits in `AnujScaffold`** (`core/ui/components/AnujScaffold.kt`). It draws edge to edge, keeps content clear of the status and navigation bars, and lifts content above the keyboard. Do not use a bare `Scaffold` or hand-rolled inset padding in a feature.
* **Keyboard never covers the focused field or its action button.** The bottom tab bar stays under the keyboard; it does not ride up with it. This is the Compose equivalent of React Native's `KeyboardAvoidingView`.
* **Short input and choices open in `AnujBottomSheet`** (`core/ui/components/AnujBottomSheet.kt`), the equivalent of an RBSheet: opens fully in one motion, closes on swipe down or tap outside, content moves up with the keyboard.
* **Shared controls, not one-off ones**: `PrimaryButton`, `SecondaryButton`, `ChoiceChips` (one of many), `ToggleChips` (any of many) and `MinTouchTarget` from `core/ui/components/Controls.kt`, so spacing and sizes match on every screen. A chip row is never laid out by hand: the shared ones leave the gap between wrapped rows, and a hand-made one without it shows the rows touching.
* **Consistent spacing**: 24dp screen side padding, 16dp between groups, 8dp between related items.
* **Bottom tab bar for the areas used all day** (Today, Tasks, Inbox, and Ask); a new area adds one entry to `TopLevelDestination` in `app/.../navigation/AnujApp.kt`. The bar is hidden on pushed screens (one task, search, trash, settings), where the back arrow is the single way out. Settings is not a tab: it opens from the menu on Today and is a tree, one row per branch (`SettingsSection`) and one screen per branch.
* **Screen titles use `ScreenHeader`**, group labels `SectionTitle`, tap-to-change values `FieldRow`, on/off values `SwitchRow`, numbers `Stepper`, times `ClockDialog` (all in `core/ui/components`). A feature's settings are a composable of `FieldRow`s that a Settings branch screen hosts — see `TaskSettingsRows` and `ReminderSettingsRows`. A feature with a full settings screen of its own (Places, Voice) is opened straight from its row in the Settings root.
* **One "Undo" host**: `AnujApp` owns the single `SnackbarHostState` and passes it to screens; a screen never creates its own.
* **Swipe needs a twin**: anything a swipe does must also be a named screen-reader action on the row and reachable by a tap. `SwipeActions` in `feature/task/common/TaskRow.kt` does this; use it rather than a bare `SwipeToDismissBox`.
* **All user-visible text comes from `strings.xml`**, never a literal in a Composable.
* **A heading only where there is something under it.** A section with nothing in it shows its "add" control and no heading; the control's own label says what it adds.
* **A list of choices is edited with chips, not typed.** The user switches candidates on and off (snooze lengths, time estimates, reminder times); the last one cannot be switched off.
* **A screen one feature draws inside another feature's screen is passed in as a slot** by `AnujApp`, the one place that knows both — see `extraFields` on `TaskDetailScreen`, which is where the reminder row comes from.

## Calm, Clear Screens
* One primary action per screen. Focus mode shows exactly one task.
* Icon and colour on every task and tracker; text is secondary.
* Simple mode: larger buttons, fewer options, everything read aloud.
* No screen may introduce a notification outside the category system (see Never Overwhelm in AGENTS.md).

## Checklist Before Calling A Screen Done
1. Can the whole flow be completed without the keyboard?
2. Can it be completed by voice alone?
3. Is the tap count within budget?
4. Does it work one-handed with the thumb?
5. Does every field have a sensible default?
6. Is there an undo?
7. With the keyboard open, are the focused field and its button both visible?
8. Does it still fit at the Extra large text size and in dark theme?
