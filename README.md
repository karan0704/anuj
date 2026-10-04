# Anuj

A native Android app that remembers things for you: nested tasks, alarms that nag, location triggers, health and habit tracking, a daily journal, offline text scanning and a voice assistant you can rename. Built for a user with ADHD, so it is designed to need very little typing — every action works by taps or by voice.

Everything runs on the phone. There is no server, and the core features need no internet.

## Status

Phases 0 to 2 are built.

- **Phase 0, foundation**: first-run setup, fingerprint or screen-lock app lock, light and dark themes, adjustable text size, encrypted database.
- **Phase 1, tasks**: nested tasks with checklists, notes, photos, tags and priority; repeating tasks with days off; carry-over rules for unfinished tasks; an inbox, search and trash; ready-made routines; backup and restore.
- **Phase 2, reminders**: reminders that go off with the app closed and come back after a restart; repeat-until-done; full-screen alarms; snooze with a reason; quiet hours, a daily limit, calm mode and a summary of what was held back; a tone per kind of reminder and per task; regular reminders for medication, water and meals; routines played one step at a time; a check that the phone lets reminders through.

Phases 0 and 1 have been run on a device by the developer. Phase 2 has only been tested on the computer so far. Phase 3 (tracking and the journal) is next.

## Build

```
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew :core:domain:test testDebugUnitTest   # all tests, including the end-to-end app journey
```

Needs JDK 17 and the Android SDK (platform 36).

## Checking the code

```
./gradlew qualityCheck
```

Runs six checks and writes a report for each. The first five only report; the architecture tests fail the run.

| Check | What it looks for | Report |
|---|---|---|
| Detekt, with ktlint | Tangled or over-long code, magic numbers, swallowed errors, formatting | `<module>/build/reports/detekt/detekt.html` |
| Android Lint | Unused resources, accessibility, wrong API use | `app/build/reports/lint-results-debug.html` |
| CPD | Copy-pasted code (`-PcpdTokens=60` finds shorter repeats) | `build/reports/cpd/cpdCheck.text` |
| Konsist | The architecture rules: no Android in `core:domain`, features independent of each other and of storage, view models given use cases only | `quality/build/reports/tests/test/index.html` |
| Dependency analysis | Libraries declared but unused, or declared in the wrong place | `build/reports/dependency-analysis/build-health-report.txt` |
| Kover | Which lines the tests never run | `build/reports/kover/htmlAnuj/index.html` |

The settings are in `config/detekt/detekt.yml` and `config/lint/lint.xml`. A new architecture rule is a new test in `quality/src/test`.

## Stack

Kotlin, Jetpack Compose, Room, Hilt, AlarmManager and WorkManager, Vosk for offline speech, ML Kit for offline text scanning.

## Where things are

- `.agents/AGENTS.md` — the rules every contributor and AI agent follows in this project
- `.agents/rules/features.md` — the full feature list by area and the build phases
- `.agents/rules/ui.md` — the minimal-typing, tap-and-voice interface rules
