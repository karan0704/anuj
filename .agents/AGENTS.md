# Master Android — Agent Configuration & Operating Rules

These files are the single source of truth for any coding tool used on this project (Claude Code, Antigravity, or another). Nothing a tool needs may live only in that tool's own memory: if it matters next time, it is written here or in a `rules/` file.

Read this file fresh from disk before starting any work in this project — it may have changed. Then read `rules/handover.md`, which says where the work stands and what is next. This file is deliberately short: it holds only the rules that apply to *every* task, plus a map of where the rest live. Open a `rules/` file only when its trigger situation actually applies — never read the whole `rules/` folder up front.

---

## 1. Project In One Paragraph

Anuj is a native Android app (Kotlin, Jetpack Compose, Room, Hilt) that puts tasks, alarms, calendar, location triggers, health/habit tracking, a daily journal and a renameable voice assistant in one place. It is built for a user with ADHD, so its job is to *remember for the user* without overwhelming them. Everything runs on the phone: database, business logic and UI all live inside the app, with no server and no internet needed for core features. The full feature list and build phases are in `rules/features.md`.

The app's name is **Anuj** (the project folder and vault folder keep their "Master Android" names). The package id is `com.karan.anuj`.

---

## 2. Always-Apply Core Rules

**3-Step Communication Format** — before modifying any code, always explain, in order:
1. What I understand from your question
2. What I understand from code and database (Inspection & Root Cause) — when this is a reported bug (not a new feature), write it as the call-chain trace in `rules/bug-trace.md`'s format, not a free-form paragraph
3. Proposed Fix (no code changes applied until explicitly instructed)

**Never Assume — Always Ask**: when a detail needed for the 3-step format above is missing or ambiguous, stop and ask instead of guessing — this applies at the start of a prompt and mid-task. See the Requirement Ambiguity Protocol below for what to do while waiting on an answer.

**Requirement Ambiguity Protocol** (what to do while a question is open): if a requirement is unclear, ask the clarifying question — but don't let work sit fully blocked on the answer. Implement the standard / common-practice version of that piece in the meantime, and mark it explicitly as an **assumption pending confirmation** (in code comments, the commit body, and the log/journal entry). Once the developer confirms, revisit that logic and adjust it to match — do not treat the standard version as final until confirmed.

**Thumb First**: the app's first job on screen is to be usable with one thumb, right or left. Anything that is tapped must be able to come down to the thumb; nothing tappable may be stuck at the top of a screen. The full rule, and what is still to be built for it, is in `rules/ui.md` under "Thumb-First Scrolling".

**A User-Facing Value Is A Setting With A Default**: a time, a length, a count, or anything the app would throw away by itself is the user's to change in Settings, and the default must be safe (the app deletes nothing unless asked). See `rules/clean-code.md`.

**Old Code Is Removed**: replaced code is deleted, not kept in comments; git history is the record. See `rules/code-integrity.md`.

**Every Build Handover Lists What To Check**: see `rules/handover.md`.

**Minimal Typing, Tap + Voice Parity**: the user should write as little as possible. Every action must be doable by taps alone *and* by voice alone; typing is always optional, never required. No screen ships with a mandatory text field that has no picker, chip, preset or microphone alternative. Details and the checklist to verify against are in `rules/ui.md`.

**Never Overwhelm**: every new reminder, prompt or nudge must belong to a notification category the user can silence, cap or move to the digest. Never add a notification that bypasses the category settings, quiet hours or calm mode.

**Schema Changes Only Through Room Migrations**: never hand-edit the on-device database or the exported schema JSON files. Every entity/column change needs a real Room `Migration` (or auto-migration) plus the regenerated exported schema. Any ad-hoc SQL needed to inspect or repair data on a device is written out for the developer to run manually in Android Studio's App Inspection — never executed directly.

**Every Table Carries The Base Record Columns**: `createdAt`, `updatedAt`, `deletedAt` (soft delete) are set in one shared place, not per feature. A new entity without them is incomplete.

**Protected Config Files**: never directly modify `local.properties`, `keystore.properties`, any `*.jks` / `*.keystore` file, or `google-services.json`. Always write out the exact snippet/lines needed and explain the change for manual application.

**Project Folder Isolation**: specs, feature docs, logs and journals for this project go exclusively under the `Master Android App` folder of the Obsidian vault — never inside another project's folder (e.g. `Civic Plan`). Full path list in `rules/paths.md`.

**Self-Audit Before Reporting Done**: before declaring a mapping, integration, or feature complete, grep every field the target (entity, UI state, voice command, notification) actually references and confirm each one traces to a real source — not a leftover mock/placeholder value. Do this on your own initiative; don't wait for the developer to ask "check again."

**Graphify First**: this is always-on, not situational — before using grep/read to answer a codebase or architecture question, query the graph (`graphify query "<question>"`, or `graphify path` / `graphify explain` for relationships and concepts) against `graphify-out/graph.json` at the project root, once it exists. After modifying any code files in a session, run `graphify update .` from the project root before considering the work done. See `rules/graphify.md`.

**Images And Screenshots**: when the developer gives an image/file path (a screenshot of a bug, a UI sketch), don't skip it — run the markitdown skill first (`D:\OneDrive\b) Documents - Personal\Obsidian\EditoraholicDesktop\Agents\Skills\markitdown\SKILL.md`, i.e. `python -m markitdown "<path>"`), and only if its output doesn't give the needed information, fall back to viewing the image directly.

---

## 3. Rule Map

Only open the file that matches your current situation:

```
.agents/
└── rules/
    ├── handover.md          [always-on: start of a session — the developer, build handover, current state, what is next]
    ├── graphify.md          [always-on: codebase/architecture questions — query graphify-out/ first]
    ├── features.md           [planning or building a feature — full feature list, phases, stack]
    ├── ui.md                 [building or changing any screen, notification or voice command]
    ├── paths.md              [need a project/vault file path]
    ├── logging.md            [writing to daily log or journal]
    ├── branch-graphify.md    [checking/updating the project's graphify output]
    ├── commit-format.md      [about to write a git commit]
    ├── feature-docs.md       [shipping a feature/bugfix, need docs]
    ├── code-integrity.md     [editing existing code / adding a feature]
    ├── clean-code.md         [writing or reviewing any code — everyday readability and design habits]
    ├── action-tracing.md     [marking a feature/fix complete — pre-commit gate]
    └── bug-trace.md          [diagnosing a reported bug — write the call-chain trace]
```

`rules/handover.md` and `rules/graphify.md` are the only always-on rule files besides this index. Update `rules/handover.md` at the end of any session that changes where the work stands.

The `skills/` folder was copied from another project and has not been reviewed for this one — treat a skill there as applicable only if it clearly fits Android/Kotlin work.
