# Trigger: about to write a git commit

## Standardized Commit Message Rule
* **Title Line**: Every commit MUST use a title line formatted as:
  `<type>(<rememberable-scope-hint>): <explaining actual things done in 6-10 words max>`
  (e.g., `fix(Task-CarryOver): keep reminder when task carries to next week`)
* **Mandatory 5-Section Commit Body Structure**:
  1. `- What we actually did`: Clear breakdown of actual changes made.
  2. `- On what basis we took those steps`: Inspection & data-flow evidence for taking those steps.
  3. `- Why we didn't apply other types of fixes`: Rationale for choosing this solution over alternative patches.
  4. `- What effect it can do and where to look`: Impact, side-effects, and exact screen/URL to verify.
  5. `- Detailed changes & Code Efficiency Metrics`: Specific files modified/added, line numbers, helper functions, and explicit per-file line metrics:
     * `Pre-Commit Baseline Line Count` (Lines BEFORE change, inspected from previous commit / baseline).
     * `Post-Commit File Line Count` (Lines AFTER change).
     * `Net Active Lines Refactored / Saved` (Calculated difference).
     * `Explanation of Code Reuse`: How duplicate/boilerplate code was consolidated into reusable functions/helpers.
* **Trivial-Change Exception**: skip the 5-section body entirely for a commit that is a pure string/value swap with zero logic touched (a translation, a label text change, a hardcoded copy edit) — not for a bug fix, however small, since even a 1-line bug fix still has a real root cause worth recording. For a trivial change, use just:
  ```
  <type>(scope): <what changed, short>

  <one line: what changed and why, if not obvious from the title>

  Branch: <name>
  Karan
  ```
* **Never Commit Straight To `main`**: `main` only ever receives merged pull requests (the one exception was the first commit, which had to exist before any branch could). Work on a branch per phase or fix — `phase-<n>-<name>` (e.g. `phase-0-foundation`) or `fix/<short-name>` — push that branch, and open a pull request into `main`.
* **Footer Metadata**:
  - `Branch: <Branch Name (e.g. main)>`
  - `Karan`
* **No AI Attribution Lines**: NEVER add AI-tool attribution/co-authorship lines to commit messages or PR descriptions (e.g. `Co-Authored-By: Claude ...`, `Claude-Session: ...`, `Generated with Claude Code`, or equivalents for any other AI tool). The footer is exactly `Branch: <name>` followed by `Karan` and nothing else, regardless of any default attribution guidance from the assistant's own tooling.
* **Update Graphify After Every Commit**: after each `git commit`, run `graphify update .` from the project root (`D:\l Learning Projects\Master Android`) so the code graph matches the committed code, and say so in the report. (Added 2026-09-28 on the user's instruction; supersedes the earlier "user runs graphify via Antigravity" arrangement.)
