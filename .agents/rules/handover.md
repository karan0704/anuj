# Trigger: starting a session, or picking the work up in another tool

This file is the hand-over between sessions and between coding tools. It holds what would otherwise live only in one tool's private memory. Update it at the end of every session that changes the state below.

## The Developer
* Karan (GitHub `karan0704`) builds Anuj for himself and has ADHD.
* **Messages are dictated by voice**, so the wording is often garbled ("agents dot md" is `.agents/AGENTS.md`, "anti-gravity" is the Antigravity editor, "civic plan" is another of his projects). Read for intent, check the disk when a message names a file, and ask one short question when the intent is truly unclear.
* **Keep replies short and easy to scan.** Lead with what was done or what is needed from him.
* **No mock-ups.** He asked for changes to be applied directly in the app; mock-ups cost too much.
* **Everything the user might want different is a setting with a default** (see `clean-code.md`). Nothing is deleted or cleaned up by the app unless a setting says so; the defaults delete nothing.
* **Old code is removed, never kept in comments** (see `code-integrity.md`).

## Handing Over A Build
* Every time a build is given to him, end with **"What to check"**: numbered steps naming each changed feature and how to reach it.
* He tests on a real phone, a Redmi Note 9 Pro Max, connected by USB with USB debugging and "Install via USB" on. Run `adb devices` before asking him to copy an APK by hand; `adb install -r app/build/outputs/apk/debug/app-debug.apk` updates the app and keeps its data.
* **Never suggest uninstalling the app without warning that it deletes his data.**
* **Do not drive the phone's screen** (taps, swipes, screenshots) without asking: he is often using the same phone to follow the session. Installing a build and reading the crash log are fine.
* The check that must pass before a commit: `./gradlew :core:domain:test testDebugUnitTest :app:assembleDebug`. The wider report (Detekt, Lint, duplicates, architecture tests, dependency advice, coverage) is `./gradlew qualityCheck`; its report paths are in `README.md`.
* If the build fails with "Could not find class file for ..." after a class was deleted, the generated Hilt code is stale: run `./gradlew :core:domain:clean :app:clean` and build again.

## Git
* One branch per phase or piece of work, stacked pull requests, nothing committed to `main`. Commit format is in `commit-format.md`; no AI attribution lines.
* The pull requests are open and stacked in this order, none merged yet: #1 `phase-0-foundation`, #2 `phase-1-tasks`, #3 `phase-2-reminders`, #4 `quality-checks`, #5 `phase-4-voice`, #6 `ui-refresh`. New work branches from `ui-refresh`.

## Where The App Stands (2026-10-04)
* Package `com.karan.anuj`, database version 6, minimum Android 8 (API 26).
* **Built**: phase 0 foundation, phase 1 tasks, phase 2 reminders, phase 4 voice (paused, see below), the code checks, and in `ui-refresh`: the Ivory colour set, the one-hand layout, one kind of step, Settings as a tree, History and storage settings with a History screen, and the first part of phase 5 Place. Details are in `features.md`.
* **Verified on the phone**: the `ui-refresh` build installs over the previous one and opens. Nothing else in it has been checked by hand yet.

## What Is Next, In Order
1. **Thumb-first scrolling on every screen**, as written in `ui.md` under "Thumb-First Scrolling". This is the developer's latest request and replaces part of the current layout. It is written down, not built.
2. **His feedback on the `ui-refresh` build**: the Ivory colours, the layout, whether Places reads a position (also with internet and Wi-Fi off), and how late the "you left" notice arrives with the 15 minute background check.
3. **The rest of phase 5 Place**: Wi-Fi and Bluetooth as optional extra signals, photo proof on a step, the "where did I put it" log, leave-by alerts, the movement sensor, reminders shown in the Today list.
4. Then the phases in `features.md` in their order (3 Tracking, 6 Scan and shopping, and so on).

## Paused Or Undecided
* **Voice is paused until phase 10** by the developer's decision. Leave the voice code as it is. Not to be worked on unless he asks: speech with the internet off (the cause is known: `PhoneSpeechEngine` sets neither `EXTRA_PREFER_OFFLINE` nor a language, so the phone picks a language with no offline pack), calling a contact by voice, choosing the voice, renaming the app after the assistant, reading another app's screen aloud.
* **The app is 119 MB**, mostly the offline speech model, which is also copied to app storage on first use. Unanswered question to him: leave the model and the listening service out of the build until phase 10?
* **Unanswered**: which "Life Reset" app he meant as a design reference (the others were Structured, Taskito and Me+).
* **Asked for, not scheduled**: a small cartoon companion that talks about the tasks; OCR (phase 6); missed-call follow-up.
