# Trigger: writing or reviewing any code

## Goal
Code is clean when anyone on the team can read it and change it, not only the person who wrote it. These are the everyday habits; the project-specific rules in `code-integrity.md` still apply and win where the two disagree (see the last section).

## Design
* **Keep configurable data at the top.** A value the user might reasonably want different (a time of day, a snooze length, how many backups are kept) lives in a settings class in `core:domain` and is shown in Settings. It is never a constant buried in a screen or a repository.
* **But do not make everything a setting.** A value becomes a setting when a real person would change it. Technical values (a database batch size, a key length, a search debounce) stay as named constants. Each new setting must have a sensible default, so the app works untouched.
* **Offer choices, not blank boxes.** A configurable list (snooze lengths, time estimates) is edited by switching chips on and off from a fixed set of candidates, not by typing numbers.
* **Prefer polymorphism to `when` chains.** A new kind of schedule, trigger, tracker or rule is a new implementation of a sealed type or interface that carries its own behaviour.
* **Inject dependencies.** A class is handed what it needs through its constructor; it never reaches out for a singleton.
* **A class talks to its direct dependencies only.** No reaching through one object to get at another's internals.
* **Keep threading code apart.** Dispatchers, scopes and locks sit in a small number of named places (`WriteScope`, repositories), not scattered through business rules.

## Understandability
* **Be consistent.** If one thing is done a certain way, every similar thing is done the same way.
* **Use explanatory variables** instead of long expressions repeated or nested.
* **Put boundary conditions in one place.** Midnight, month ends, an empty list, "no value yet": handle each in one function, not at every call site.
* **Prefer value types to bare primitives**: `TaskId`, `LocalTime`, `ReminderId`, not `String` and `Int`.
* **Avoid negative conditionals.** `isOpen` reads better than `!isClosed`.
* **No hidden order dependence.** A function must not only work if another function in the same class was called first.

## Names
* Descriptive, unambiguous, pronounceable and searchable.
* A named constant for every number that means something (`MINUTES_PER_DAY`, not `1440`).
* No type or scope prefixes (`strName`, `mList`).

## Functions
* Small, doing one thing, named for what they do.
* Few arguments. No boolean flag that selects between two behaviours: write two functions.
* No surprise side effects.

## Comments
* Explain intent, a non-obvious reason, or a consequence to be warned about. Do not restate what the code already says.
* No closing-brace comments, no noise.

## Source layout
* Related code close together; a caller above the functions it calls.
* Variables declared near where they are used.
* Short lines; no horizontal alignment of assignments.

## Objects and data
* Hide internal structure. Small classes with few fields, each doing one thing.
* A base type knows nothing about its subtypes.

## Tests
* Readable, fast, independent and repeatable.
* One behaviour per test, named as a sentence. Several asserts are fine when they check one behaviour.

## Smells to stop and fix
Rigidity (one change forces many), fragility (one change breaks distant code), immobility (nothing can be reused), needless complexity, needless repetition, opacity.

## Where This Project Differs, And Which Rule Wins
* **"Don't comment out code, just remove it."** Followed as written since 2026-10-04: old code is deleted and git history keeps it.
* **"Don't be redundant in comments."** `code-integrity.md` asks for a comment where a change is not obvious. Both hold: the comment must say *why*, never repeat *what*.
* **"One assert per test."** Read here as one behaviour per test.
