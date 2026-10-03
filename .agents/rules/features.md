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

`app` and the four `core` modules below it exist (phase 0). `core/backup` and every `feature/` module are planned and not built yet. Package root: `com.karan.anuj` (**assumption pending confirmation**). minSdk 26, also an assumption.

```
app/                      navigation, Hilt entry point
core/domain/              entities, repository interfaces, use cases (pure Kotlin)
core/data/                Room database, DAOs, repository implementations
core/ui/                  theme, shared Compose components
core/security/            app lock, database encryption
core/backup/              backup, restore, export
feature/task/             nested tasks, checklist, repetition, carry-over, tags, search, trash
feature/reminder/         alarms, nagging, notification categories, health check
feature/tracker/          trackers, entries, charts, journal
feature/voice/            wake name, commands, voice notes
feature/place/            location + Wi-Fi triggers, leaving-home checklist
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
- `TextScanner` — the OCR engine, so it can be swapped without touching the screens that use it

---

## Features By Area

The number after each feature is the phase it is built in.

### A. Tasks
| Feature | Phase |
|---|---|
| Nested tasks to any depth (task inside task inside task) | 1 |
| Checklist, name, description, time | 1 |
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
| 0 | Foundation | Project + modules, encrypted Room database with base columns and change history, navigation, theme with dark mode and text size, app lock, permissions onboarding, test setup | App opens, locks, and has an empty home screen | Built and unit-tested; not yet run on a phone |
| 1 | Tasks | Area A, plus backup and restore | A full to-do app with nesting, carry-over, search, tags and safe data | Not started |
| 2 | Reminders | Area B (except leave-by) | Tasks remind and nag reliably, without overwhelming | Not started |
| 3 | Tracking | Area C (except mood log and watch import) | Sleep, water, food, weight, BP, journal, doctor export | Not started |
| 4 | Voice | Area D | Hands-free: named assistant, voice notes, voice add | Not started |
| 5 | Place | Area E, plus leave-by alerts | Leaving-home checklist and location reminders | Not started |
| 6 | Scan and shopping | Areas F and G | Scan pages and receipts, shopping list, purchases, bills | Not started |
| 7 | Phone use and focus | Area H | Phone-use nudges, focus mode, pick for me | Not started |
| 8 | Visual | Area I (rest) | Calendar, timeline, widget, simple mode | Not started |
| 9 | Motivation | Area J, plus mood and energy log | Streaks, companion, reviews, task breakdown | Not started |
| 10 | Beyond the phone | Area L, emergency card, Health Connect | Watch, calendar sync, optional server | Not started |

Phases 0–2 are the minimum that makes the app worth using daily. Phases 3–6 can be reordered freely since none depends on another, except "shopping list opens at the shop", which needs phase 5.

---

## Feature Details Worth Not Re-Deriving

**Task tree** — one `task` table with a `parentId` column. Fields: name, description, checklist items, due time, repetition rule, days off, priority, tags, energy tag, estimated minutes, carry-over rule, carry count, optional tracker, optional per-task notification tone, photo attachments.

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

**Backup** — one encrypted file containing the database, voice recordings and photos, written on a schedule to a folder the user picks. Restore is tested as part of phase 1, not assumed.

**Automatic task breakdown** — cannot be done well offline: needs an online AI model or built-in templates. **Not decided yet.**

**Server / gRPC** — the app has no backend. gRPC only applies if device-to-device sync or a web version is wanted later (phase 10). **Not decided yet.**

## Ideas Noted In The Vault, Not Scheduled

`Master Android App/Info/` in the Obsidian vault also lists "YouTube Premium" (download/play files and playlists, comments, playlist CRUD) and "Live File Preview on Devices". These are not part of any phase above until the developer says so.
