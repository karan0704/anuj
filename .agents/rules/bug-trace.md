# Trigger: diagnosing a reported bug

## Written Trace Before Root-Causing
When investigating a reported bug, write the trace out instead of just holding it in your head — this becomes step 2 (Inspection & Root Cause) of the 3-Step Communication Format in AGENTS.md, not an extra step. It turns tracing you'd do anyway into something the developer can read, and something the *next* bug on the same feature can reuse instead of re-deriving the whole call chain from scratch.

Format:

```
## Bug Trace: <short title>

**Symptom**: exact screen / user action (tap, voice command, notification), and what breaks
**Device / Android version**: (alarms, background work and microphone rules differ by version and phone maker)
**Entry point**: Composable screen file — or the Receiver / Service / Worker / widget if it starts outside the app
**ViewModel**: file:line — function called, UI state changed
**Use case**: file:line — function name
**Repository**: interface + implementation file:line
**DB**: DAO query, table(s) + column(s) touched
**Scheduling / system API** (if any): AlarmManager, WorkManager, geofence, usage stats, speech
**Permissions involved**: which, and whether granted
**Where to check logs**: exact Logcat tag or grep pattern
**Sibling implementations to check**: other Trigger / VoiceCommand / TrackerType / CarryOverRule classes (per Sibling Implementation Tracing in action-tracing.md)
**Root cause**: (filled in once found)
**Fix + commit**: (filled in once fixed)
```

Leave a field blank rather than guess at it — a blank `Where to check logs` is honest, a made-up one is worse than nothing.
