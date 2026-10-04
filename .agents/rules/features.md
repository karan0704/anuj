# Trigger: planning or building a feature

## Stack (everything on the device — no server)

| Part | Choice |
|---|---|
| Language | Kotlin |
| Frontend | Jetpack Compose (lazy lists for long task trees) |
| Business logic ("backend") | Use cases + repository interfaces in pure-Kotlin modules, no Android dependency |
| Database | Room (SQLite), encrypted, indexes on `parentId` and due time, Flow for live UI updates |
| Background | AlarmManager for exact alarms, WorkManager for everything else |
| Wiring | Hilt — features depend on interfaces, never on each other |
| Speech | Vosk (offline, English model) for the wake name and transcription, Android text-to-speech for replies |
| Text scanning (OCR) | ML Kit Text Recognition with the bundled on-device model — works offline, behind a `TextScanner` interface |
| Speed | R8 shrinking, Baseline Profiles, no database, file, speech or scan work on the main thread |

App language and speech language: English only.

Library versions are pinned in `gradle/libs.versions.toml`. They are a known-working mid-2025 set (Kotlin 2.1.20, AGP 8.12.0, Gradle 9.0.0, Compose BOM 2025.06.01, Room 2.7.2, Hilt 2.56.2), chosen because they were already cached on this machine and are known to work together — not the newest available. Upgrade them together, as its own change, not in the middle of a feature.

## Module Layout

`app`, the five `core` modules, `feature/task`, `feature/reminder` and `feature/voice` exist (phases 0 to 2, and 4), plus `quality` (tests about the code). Every other `feature/` module is planned and not built yet. Package root: `com.karan.anuj` (**assumption pending confirmation**). minSdk 26, also an assumption.

```
app/                      navigation, Hilt entry point
core/domain/              entities, repository interfaces, use cases (pure Kotlin)
core/data/                Room database, DAOs, repository implementations
core/ui/                  theme, shared Compose components
core/security/            app lock, database encryption
core/backup/              backup, restore, export
feature/task/             nested tasks (a step is a sub-task), repetition, carry-over, tags, search, trash
feature/reminder/         the alarm, notifications, full-screen alarm, reminder settings, regular reminders, routine player, reminder check
feature/tracker/          trackers, entries, charts, journal
feature/voice/            wake name, commands, voice notes
feature/place/            reading location, the background check, the Places screen, the place row on a task
feature/scan/             offline OCR, share-to-Anuj
feature/shopping/         shopping list, purchases, bills, expenses
feature/phoneuse/         usage nudges, app limits, focus mode
feature/calendar/         calendar, timeline, widget, tile
feature/motivation/       streaks, companion, achievements, reviews
```

Extension points that must stay as interfaces so new variants need no edits to existing code:
- `Trigger` — time, location, Wi-Fi, phone-use
- `VoiceCommand` — one handler class per spoken command
- `TrackerType` — duration, number, pair, counter, scale, amount-with-text
- `CarryOverRule` — next day, next week/month/year, in N days, specific date, ask me, don't carry
- `ReminderSchedule` — before a task's time, at clock times, every N minutes, once; each kind works out its own next time
- `TextScanner` — the OCR engine, so it can be swapped without touching the screens that use it

---

## Features By Area

The number after each feature is the phase it is built in.

### A. Tasks
| Feature | Phase |
|---|---|
| Nested tasks to any depth (task inside task inside task) | 1 |
| Steps, name, description, time | 1 |
| Repetition with days off | 1 |
| Inbox for unsorted thoughts | 1 |
| Typed notes | 1 |
| Carry-over rules for unfinished tasks (next day / week / month / year / N days / date / ask me / don't carry) | 1 |
| Priority level and coloured tags | 1 |
| Photos attached to a task (bill, prescription, where you parked) | 1 |
| Search across tasks and notes | 1 |
| Trash screen with restore | 1 |
| Ready-made templates on first run (morning routine, leaving home, water, sleep) | 1 |
| Energy tag (low / medium / high) and estimated minutes | 1 |

### B. Reminders And Notifications
| Feature | Phase |
|---|---|
| Exact alarms that fire with the app closed, rescheduled after reboot | 2 |
| Nagging repeats until done or snoozed with a reason | 2 |
| Full-screen alarm for critical tasks | 2 |
| Routines played step by step | 2 |
| Medication, water and meal reminders | 2 |
| Notification categories with own tone, quiet hours, daily cap, digest, calm mode, per-task tone | 2 |
| Reminder health check (tests alarms, walks through battery-saver settings per phone maker) | 2 |
| Transition warnings ("10 minutes left, then switch") | 2 |
| Appointment prep: "things to bring" list per event | 2 |
| Follow-up reminders: missed call or "reply to this person" becomes a task in one tap | 2 |
| Leave-by alert that includes travel time | 5 |

### C. Tracking, Health And Journal
| Feature | Phase |
|---|---|
| Tracker types: duration, number, pair, counter, scale, amount-with-text | 3 |
| Entries with edit, delete, undo, typed or voice note | 3 |
| User-defined trackers | 3 |
| Daily summaries and weekly trend charts | 3 |
| Daily journal (own notes + automatic done / remaining / achievements / totals) | 3 |
| Search across journal text | 3 |
| Export a date range for a doctor as PDF or spreadsheet | 3 |
| Bedtime wind-down reminder tied to the sleep tracker | 3 |
| Mood and energy one-tap log | 9 |
| Import from a watch or band through Health Connect | 10 |

### D. Voice
| Feature | Phase |
|---|---|
| Voice notes with transcript, audio kept beside the text | 4 |
| Voice quick-add ("call mum tomorrow 6pm") | 4 |
| Assistant with a user-changeable wake name | 4 |
| Commands: flashlight, add task, what's next, timer, mark done, log water / sleep, undo | 4 |
| Voice search | 4 |
| Listen only while charging / screen on option | 4 |

### E. Place
| Feature | Phase |
|---|---|
| Leaving and arriving triggers | 5 |
| Home Wi-Fi and Bluetooth triggers | 5 |
| Leaving-home checklist (keys, door, lights, tap) | 5 |
| Photo proof for checklist items | 5 |
| "Where did I put it" log | 5 |
| Places the phone notices by itself: a spot stayed at for about an hour, or returned to often, is offered for saving with a name or a tag | 5 |
| A task tied to a saved place (the weekly water can at the shop) | 5 |

**A noticed spot is the user's call, three ways** (2026-10-04): when the phone notices a spot, it asks once and offers (1) save it as a place, named now or edited later, (2) keep it only as "visited", which is a record of having been there and never triggers a task or a reminder, or (3) ignore it. A saved place can be renamed, re-tagged or turned back into "visited" afterwards. The question is a notification of the Place kind, so quiet hours, the daily limit and calm mode apply to it.

**Place must work offline, and with Wi-Fi and Bluetooth switched off** (developer's requirement, 2026-10-04): no feature of area E may need the internet, and none may need Wi-Fi or Bluetooth to be on, because the developer keeps both off by habit. Location is the one signal that must always be enough on its own: Google's location service first, the phone's own GPS as the fallback when Google gives nothing. Wi-Fi and Bluetooth are optional extras that make a place quicker to recognise when they happen to be on. This rules out anything that looks a place up online (maps, addresses, travel time from a server).

- Indoors, with no radio to help, a GPS fix can be missing for a long time. "Still at home" is therefore held from the last good position plus the phone's movement sensor (no radio needed): the app does not decide the user has left until a position outside the circle is actually seen.
- Before: this paragraph said "Place must work offline, from GPS alone ... Wi-Fi and Bluetooth are extra signals, never the only one."

**Leaving-list ticks survive passing by** (2026-10-04): the list unticks only when the phone is sure the user is inside — the home Wi-Fi connected, or a set time spent at home — never on merely coming near.

### F. Scan (Offline OCR)
| Feature | Phase |
|---|---|
| Scan a printed page with the camera and get its text | 6 |
| Read text from a screenshot or saved image (e.g. a web page) | 6 |
| Send scanned text to a note, a task, or the journal | 6 |
| Share-to-Anuj: share text, a link or an image from any app into the inbox | 6 |
| Scan a receipt into the purchase log | 6 |

### G. Shopping And Money
| Feature | Phase |
|---|---|
| Shopping list of things to buy, tick off by tap or voice | 6 |
| Purchase log: what was bought, price, where, when | 6 |
| Re-buy reminders for things bought regularly | 6 |
| Shopping list opens on arriving at the shop | 6 |
| Bill due-date reminders | 6 |
| Simple expense log with monthly totals | 6 |

### H. Phone Use And Focus
| Feature | Phase |
|---|---|
| Long-use nudges from sensors, with time held | 7 |
| Per-app usage limits | 7 |
| Pause before a distracting app opens | 7 |
| Focus mode: one task on screen, timer, break reminder | 7 |
| "Just 2 minutes" start button | 7 |
| Hyperfocus guard: drink / eat / stand reminders after a long stretch | 7 |
| Focus sounds (white noise, rain) | 7 |
| Pick for me: chooses a task that fits current energy and time | 7 |

### I. Visual And Quick Access
| Feature | Phase |
|---|---|
| Dark theme and adjustable text size | 0 |
| Calendar view | 8 |
| Day timeline with icons and colours | 8 |
| Shrinking-disc timer | 8 |
| Home-screen widget and quick-settings tile | 8 |
| Simple mode: large buttons, icons, read-aloud | 8 |

### J. Motivation And Review
| Feature | Phase |
|---|---|
| Forgiving streaks | 9 |
| Growing companion | 9 |
| Achievements page | 9 |
| Estimated vs actual time | 9 |
| Daily plan and evening review (uses the "ask me" carry-over rule) | 9 |
| Automatic task breakdown | 9 |
| Accountability partner: daily summary sent through WhatsApp or SMS | 9 |

### K. Safety, Privacy And Data
| Feature | Phase |
|---|---|
| Base record columns (`createdAt`, `updatedAt`, `deletedAt`) and change-history table | 0 |
| Fingerprint app lock and encrypted database | 0 |
| Permissions onboarding | 0 |
| Unit-test setup; date-math tests grow with every phase | 0 |
| Backup and restore to a user-chosen folder (including Google Drive through the system file picker), on a schedule | 1 |
| Emergency card: medical details and a contact, shown as a lock-screen notification | 10 |

### L. Beyond The Phone
| Feature | Phase |
|---|---|
| Google Calendar sync | 10 |
| Wear OS watch: reminders and one-tap done | 10 |
| Server and sync between devices (gRPC would be used here) — only if a backend is wanted | 10 |
| Play Store release (needs policy approval for background location, usage access, always-on microphone) | 10 |

---

## Phases

Each phase must end in an installable, usable app (see Phased Independent Testability in `code-integrity.md`). Update the Status column as phases move.

| Phase | Name | What gets built | Usable result | Status |
|---|---|---|---|---|
| 0 | Foundation | Project + modules, encrypted Room database with base columns and change history, navigation, theme with dark mode and text size, app lock, permissions onboarding, test setup | App opens, locks, and has an empty home screen | Built; run on a device by the developer. The step-by-step guide has not been confirmed |
| 1 | Tasks | Area A, plus backup and restore | A full to-do app with nesting, carry-over, search, tags and safe data | Built; run on a device by the developer, two layout faults reported and fixed. The step-by-step guide has not been confirmed |
| 2 | Reminders | Area B (except leave-by) | Tasks remind and nag reliably, without overwhelming | Built and tested on the computer; not yet run on a phone. Missed-call follow-up is not built (see Phase 2 gaps) |
| 3 | Tracking | Area C (except mood log and watch import) | Sleep, water, food, weight, BP, journal, doctor export | Not started |
| 4 | Voice | Area D | Hands-free: named assistant, voice notes, voice add | Built and tested on the computer; speech itself has not been run on a phone. Several items are open (see Phase 4 gaps) |
| 5 | Place | Area E, plus leave-by alerts | Leaving-home list and location reminders | Part built (see Phase 5 status). The rules are tested on the computer; reading location on a phone has not been verified |
| 6 | Scan and shopping | Areas F and G | Scan pages and receipts, shopping list, purchases, bills | Not started |
| 7 | Phone use and focus | Area H | Phone-use nudges, focus mode, pick for me | Not started |
| 8 | Visual | Area I (rest) | Calendar, timeline, widget, simple mode | Not started |
| 9 | Motivation | Area J, plus mood and energy log | Streaks, companion, reviews, task breakdown | Not started |
| 10 | Beyond the phone | Area L, emergency card, Health Connect | Watch, calendar sync, optional server | Not started |

Phases 0–2 are the minimum that makes the app worth using daily. Phases 3–6 can be reordered freely since none depends on another, except "shopping list opens at the shop", which needs phase 5.

---

## Feature Details Worth Not Re-Deriving

**Phase 1 gaps, known and deliberate** — none of these block daily use; each is small enough to add when it is missed:
- A task cannot be moved under a different parent, and rows cannot be reordered by hand (order is priority, then age).
- A note and a tag can be added and removed but not edited in place.
- (Closed.) The part-of-day chips were fixed times; they are now a setting, "Times of day".
- (Closed.) The trash is emptied by itself only if the user chooses a period in Settings, History and storage; the default is never.
- A task has no voice path yet (phase 4); until then a task name is typed, dictated with the keyboard's own microphone, or picked from the "Add again" chips.
- Deleting from the task screen shows no "Undo" message, because the screen closes; the task is restored from the trash instead.
- Not exercised by any test, because they need a phone: the encrypted database, the camera and photo picker, choosing a backup folder, and the scheduled backup.

**Phase 2 gaps, known and deliberate**:
- **Follow-up from a missed call is not built.** It needs the call-log permission and a phone to test on; a "reply to this person" task is added like any other task. This is the one item of area B that is open.
- Nothing has been run on a phone. Whether a given phone lets the alarm through with the app closed is exactly what cannot be shown on the computer; the in-app Reminder check exists to find that out.
- The routine player's countdown and "time nearly up" warning only run while its screen is open. A reminder set on the routine or on a step still arrives with the app closed.
- Reminder edits are not written to the change history (task edits are).
- Reminder settings live in a settings file and are not part of a backup; reminders themselves and their log are.
- Medication, water and meal reminders only remind. Counting glasses or doses is phase 3 (trackers).
- The app lock does not cover the answer screen: it shows a reminder's name and its answers over the lock screen, as the notification itself already does.

**Phase 4 gaps, known and deliberate**:
- **Nothing spoken has been tried on a phone.** The rules that read a sentence are tested; whether the engine hears the sentence correctly is not.
- **The audio of a voice note is not kept.** Speaking fills the text field; the recording itself is thrown away.
- The microphone button is on the new-task name only. Notes, descriptions, checklist lines, search and the inbox do not have it yet.
- "Log water" and "log sleep" wait for trackers (phase 3).
- Voice has no reminder commands yet ("snooze", "remind me in an hour" as a reminder rather than a task).
- The topic words behind "office tasks" and "tasks related to money" have defaults but no screen to change them.
- The engine is Vosk's small English model: fast and light, less accurate than larger engines. It is behind `SpeechEngine`, so it can be swapped.
- Asked for on 2026-10-04 and not built: calling a contact by voice (phone app or WhatsApp, asking which one when several match); choosing the assistant's voice; using the assistant's name as the app's name on every screen; reading text on another app's screen aloud (needs the accessibility permission, and the scanner from phase 6).

**Voice** — the assistant is a list of `VoiceCommand`s in `core:domain`, asked in order; the first one that recognises the sentence answers. A tapped shortcut sends the same sentence a voice would, so tap and voice end in the same use case. `VoiceAssistant` remembers what the last command changed, which is what "undo" takes back.

- **The name is learnt, not spelt.** An offline engine only writes dictionary words, so "Anuj" comes out as other words. Settings, Voice, "Teach it its name" records what the engine writes, and any of those phrases wakes the assistant (`VoiceSettings.soundsLike`).
- **Listening for the name** is a foreground microphone service with its own low-importance notification, started only while the app is on screen (Android refuses otherwise). It is off until the user chooses when it listens.
- **The speech model is not in the repository.** `feature/voice` downloads it (about 40 MB) the first time it is built and packs it into the app, so the first build needs the internet and the phone never does.
- **The microphone on a text field** comes through `LocalVoiceInput` in `core:ui`, supplied once by `AnujApp`, so no feature depends on the voice feature.

**Reminders** — one `reminder` table. A reminder either belongs to a task (timed from the task's day and time, minus a lead) or stands on its own with clock times (medication, water, meals). Its next time is never stored: `ReminderPlanner` works it out from the schedule and from what has already shown, so it cannot go stale.

- **One alarm, one sync.** The phone holds a single wake-up, for the earliest due moment. `SyncRemindersUseCase` shows everything due, then sets the next wake-up. It is safe to run any number of times and is run by the alarm, a restart, a clock or zone change, the app coming to the front, and any change to a task, a reminder or the settings (`ReminderRunner` watches for those). **Never set an alarm or post a reminder notification from a feature**: change the data and the sync follows.
- **Every notification passes `NotificationPolicy.decide`**, which applies the kind's on/off switch, "wait for the summary", calm mode, quiet hours and the daily limit. A held reminder is listed in the next summary (at the user's summary times, and when quiet hours end).
- **Kinds** (`ReminderCategory`): task, alarm, early warning, regular, summary, place. Each has its own tone, vibration and rules. Android fixes a channel's sound when the channel is made, so the tone is part of the channel id and choosing another tone replaces the channel.
- **Repeats** stop when the reminder is answered, its limit is reached, or (for a task) the task is finished or moves to another time.
- **A task with a time is reminded at that time without being asked** (a setting). Removing a task's reminder keeps the removed row, which is what stops the automatic one from coming back.
- **Things to bring** are the task's steps not ticked yet, shown in its reminder.
- **Full-screen alarm**: a notification with a full-screen intent to `ReminderActivity`, on a channel that plays at the alarm volume, and it keeps sounding until answered.
- **Settings, not constants**: snooze lengths, repeat gaps, "remind me" times, snooze reasons, quiet hours, the daily limit, summary times and the routine warning are all in `ReminderSettings`, each with a default. Lists are edited by switching candidates on and off (see `rules/clean-code.md`).

**Steps (2026-10-04)** — there is one kind of step: a sub-task. The separate checklist was removed in database version 4, whose migration turned every checklist line into a step. Do not add a second, lighter kind of line again.

**Screens (2026-10-04)** — three tabs (Today, Tasks, Inbox) and Ask. A tab's list is a `OneHandList`: the read-only top (clock, title) never moves, the list starts lowered, and search and the menu ride at its head on the side of the chosen hand. A screen opens with few controls; the rest is under a `MoreRow` that says what is set. Settings is a tree opened from the home menu: one row per branch, one screen per branch.

**Phase 5 status (2026-10-04)** — built: places (saved, only visited, ignored, home), noticing a spot after a stay or after returning often and asking once, a task tied to a place for arriving or for leaving, the leaving list (told which steps are open on leaving; steps start fresh only after settling back home), Google location first with GPS as fallback, a check each time the app opens and about every 15 minutes in the background when "Allow all the time" is granted. Not built yet: Wi-Fi and Bluetooth as extra signals, photo proof on a step, the "where did I put it" log, leave-by alerts, the movement sensor for staying "at home" indoors (the rule "a vague reading never means left" covers that case for now), and showing reminders in the Today list.

**Task tree** — one `task` table with a `parentId` column. Fields: name, description, due time, repetition rule, days off, priority, tags, energy tag, estimated minutes, carry-over rule, carry count, optional tracker, optional per-task notification tone, photo attachments.

**Carry-over** — applied when a task is unfinished at end of day. Set at three levels: app default → parent task/list → single task. Respects days off. A missed occurrence of a repeating task is marked missed instead of piling up, unless carry-over is switched on for it. After a user-set number of carries the app asks whether to break it down, reschedule or drop it.

**Trackers** — any task can own a tracker; a recurring task (e.g. Sleep) collects many entries per day.

| Type | Used for | One entry stores |
|---|---|---|
| Duration | Sleep, naps, meals, activities | Start, end, computed hours |
| Number | Weight, heart rate, temperature | Value, unit, time measured |
| Pair | Blood pressure | Two values, time |
| Counter | Glasses of water | One tap = +1, each timestamped |
| Scale 1–5 | Energy, mood, health | Level, time |
| Amount with text | Food | What, how much, how long |

Every entry can carry a typed or voice note. Heart rate is typed in or imported through Health Connect — the phone cannot measure it reliably.

**Daily journal** — one page per day: the user's typed/voice notes, plus an automatic half (tasks completed, tasks remaining, achievements, tracker totals). Searchable, reachable from the calendar.

**Voice assistant** — Vosk listens for the user's chosen name inside a foreground microphone service (permanent notification, real battery cost; offer "only while charging / screen on"). The name is plain text in settings, changeable any time. May need the app opened once after a reboot on recent Android versions.

**Voice notes** — every note/description field offers type or record; audio is kept beside the transcript. Only the English speech model is bundled (~50 MB).

**Scan (OCR)** — ML Kit's bundled Latin-script model runs fully on the device. Input is the camera, a picked image, or an image shared from another app (this is how a web page is scanned: screenshot, then share to Anuj). Printed text reads well; handwriting and low light do not. A scanned receipt gives raw text — pulling out item names and prices is best-effort and always shown for the user to correct before saving.

**Shopping and purchases** — a shopping item becomes a purchase record when ticked off (price and shop optional). Items bought repeatedly can carry a re-buy interval that puts them back on the list.

**Backup** — one zip file (`manifest.json`, `data.json` with every row, `attachments/` with the photos) written to a folder the user picks with the system file picker, by hand or every day / week; the newest few are kept (10 unless changed in Settings). A password is optional: with one, the rows and photos are AES-256 encrypted inside the zip and the file still opens in any zip tool that supports AES. Rows are stored as JSON rather than as a copy of the database file because that file is encrypted with a key that cannot leave the phone. Restore replaces everything, and is tested: every way it can fail leaves the existing data untouched.

**Automatic task breakdown** — cannot be done well offline: needs an online AI model or built-in templates. **Not decided yet.**

**Server / gRPC** — the app has no backend. gRPC only applies if device-to-device sync or a web version is wanted later (phase 10). **Not decided yet.**

## Ideas Noted In The Vault, Not Scheduled

`Master Android App/Info/` in the Obsidian vault also lists "YouTube Premium" (download/play files and playlists, comments, playlist CRUD) and "Live File Preview on Devices". These are not part of any phase above until the developer says so.
