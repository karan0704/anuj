# Trigger: writing to daily log or journal

## Daily Logging, Journaling, Timestamping & Per-Entry Authorship Rule
* **Mandatory Logs & Journals**: For every session where dev work or analysis occurs, create or append to both the daily log and daily journal under the project vault folder (see `rules/paths.md`):
  - **Logs**: `.../Master Android App/Logs/[MonthName]/YYYY-MM-DD.md`
  - **Journaling**: `.../Master Android App/Journaling/[MonthName]/YYYY-MM-DD.md`
* **No Redundant Project Header**: Do NOT add `* **Project**: Master Android` in file headers because the directory path already defines the project.
* **Per-Entry Timestamp & Authorship Metadata**: Multiple entries on the same date can be written by different AI assistants (e.g., Antigravity AI, Claude). Every section block MUST explicitly attach its own **Author** and **Timestamp** metadata:
  ```markdown
  ## [Section Title]
  * **Author**: Antigravity AI (Gemini / AGY) | Claude (Anthropic)
  * **Timestamp**: YYYY-MM-DD HH:mm:ss IST
  ```
* **Structured Bullet Points**: All log and journal entries MUST be written in clean, concise bullet points under clear section headers.
* **Human-Readable Content, Not AI-Narrator Voice**: write entries the way a developer logging their own day would, not as an AI describing its own actions:
  - Lead each bullet with the plain-language meaning ("a task carried to next week lost its reminder") before the technical detail (function/file names) — the technical part is supporting evidence, not the subject of the sentence.
  - One idea per bullet. Don't pack diagnosis + fix + files + commit hash into a single run-on bullet — split them so each is skimmable on its own.
  - **Logs answer "what shipped"** (files, commit hash, one-line description). **Journal answers "why it matters / what pattern it reveals"** — don't just restate the log entry in different words under Journal.
  - Don't narrate the diff — the commit already shows what changed line by line. Spend the words on why this approach and what it affects for a real user.
  - No date-stamped "per user instruction" framing, same as the code-comment rule in `code-integrity.md` — write it as your own observation, not an instruction log.
* **Global Redirection Links**: Always create or update the global vaults (`EditoraholicDesktop/Logs/[MonthName]/YYYY-MM-DD.md` and `EditoraholicDesktop/Journaling/[MonthName]/YYYY-MM-DD.md`), appending redirection links:
  ```markdown
  - [[Master Android App/Logs/[MonthName]/YYYY-MM-DD|Master Android Dev Log]]
  - [[Master Android App/Journaling/[MonthName]/YYYY-MM-DD|Master Android Dev Journal]]
  ```
