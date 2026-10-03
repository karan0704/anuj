# Anuj

A native Android app that remembers things for you: nested tasks, alarms that nag, location triggers, health and habit tracking, a daily journal, offline text scanning and a voice assistant you can rename. Built for a user with ADHD, so it is designed to need very little typing — every action works by taps or by voice.

Everything runs on the phone. There is no server, and the core features need no internet.

## Status

Phases 0 and 1 are built. Phase 0 is the foundation: first-run setup, fingerprint or screen-lock app lock, light and dark themes, adjustable text size, encrypted database. Phase 1 is tasks: nested tasks with checklists, notes, photos, tags and priority; repeating tasks with days off; carry-over rules for unfinished tasks; an inbox, search and trash; ready-made routines; backup and restore. Phase 2 (reminders) is next.

Neither phase has been run on a phone yet; both are tested on the computer.

## Build

```
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew :core:domain:test testDebugUnitTest   # all tests, including the end-to-end app journey
```

Needs JDK 17 and the Android SDK (platform 36).

## Stack

Kotlin, Jetpack Compose, Room, Hilt, AlarmManager and WorkManager, Vosk for offline speech, ML Kit for offline text scanning.

## Where things are

- `.agents/AGENTS.md` — the rules every contributor and AI agent follows in this project
- `.agents/rules/features.md` — the full feature list by area and the build phases
- `.agents/rules/ui.md` — the minimal-typing, tap-and-voice interface rules
