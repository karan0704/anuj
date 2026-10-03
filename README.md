# Anuj

A native Android app that remembers things for you: nested tasks, alarms that nag, location triggers, health and habit tracking, a daily journal, offline text scanning and a voice assistant you can rename. Built for a user with ADHD, so it is designed to need very little typing — every action works by taps or by voice.

Everything runs on the phone. There is no server, and the core features need no internet.

## Status

Phase 0 (foundation) is built: the app opens, walks through first-run setup, can lock with fingerprint or screen lock, has light and dark themes and adjustable text size, and stores data in an encrypted database. Phase 1 (tasks) is next.

## Build

```
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew :core:domain:test      # unit tests
```

Needs JDK 17 and the Android SDK (platform 36).

## Stack

Kotlin, Jetpack Compose, Room, Hilt, AlarmManager and WorkManager, Vosk for offline speech, ML Kit for offline text scanning.

## Where things are

- `.agents/AGENTS.md` — the rules every contributor and AI agent follows in this project
- `.agents/rules/features.md` — the full feature list by area and the build phases
- `.agents/rules/ui.md` — the minimal-typing, tap-and-voice interface rules
