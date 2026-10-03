# Trigger: shipping a feature/bugfix, need docs

## Feature Documentation & Testing Guide Rule
* **Dedicated Feature Folder**: Whenever implementing a feature or bugfix, create a dedicated documentation folder under the project's Obsidian feature-docs root (see `rules/paths.md`):
  - `.../Master Android App/Features/[Feature Name]/` (e.g. `Features/Carry-Over Rules/`)
* **Mandatory Feature & Test Guide (`.../[Feature Name]/Guide.md`)**: Write a detailed guide containing:
  1. **What We Changed**: Clear summary of modified files, line numbers, and logic.
  2. **Why We Took These Steps**: Data flow inspection and rationale.
  3. **Step-by-Step Testing Guide**: Exact on-device testing instructions (screen, taps, voice command to say, expected result), including what to check with the app closed for alarms, location and other background behavior.
* **Keep It Human-Readable**: open with a one-line plain-language summary a non-technical reader can understand without reading further. Keep the same short template every time — don't let "detailed" turn into "long"; a reader should be able to tell from the first line whether they need the rest.
* **Update The Phase Table**: when a phase or feature ships, update its Status in `rules/features.md` in the same change.
