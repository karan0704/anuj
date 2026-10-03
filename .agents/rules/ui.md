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
* Bottom sheets instead of full-screen forms; optional fields collapsed under "More".
* Swipe right = done, swipe left = snooze / carry over.
* Undo snackbar instead of "Are you sure?" dialogs, except for irreversible actions.

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
