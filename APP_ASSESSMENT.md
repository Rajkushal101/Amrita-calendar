# ACN Planner: current condition and product direction

Assessment date: 11 September 2026.

## Product understanding

The goal is an academic app for your college's students. The existing project points specifically toward Amrita Chennai: ACN Planner branding, an AUMS login flow, academic dates for 2026–27, and default CYS profile values. Campus and department defaults in code are evidence of the prototype's starting point, not confirmed limits on the intended audience.

The useful student experience is: open the app and know today's classes, where to go, what is due, attendance standing, upcoming exams, and relevant college notices. Subjects should bring together attendance, syllabus, projects, and tasks. AI can help retrieve information and import documents once those underlying records are dependable.

Assessment: a substantial functional prototype with incomplete integrations and data-integrity problems. It is not ready for a college-wide rollout. The current Kotlin/Compose/Room foundation is worth improving; this assessment does not establish a need for a rewrite.

## Existing implementation

Single Android application module, Kotlin, Jetpack Compose/Material 3, Navigation Compose, ViewModels and Flow, Room database version 7, Firebase anonymous authentication and Firestore, WorkManager, a WebView/Jsoup AUMS import, and a Gemini assistant using a student-provided API key.

| Area | What exists | Present limitation |
| --- | --- | --- |
| Onboarding | Student/faculty role selection saved locally | No verified membership or complete student profile setup |
| Home | Schedule, current/next class, attendance alerts, due tasks | Placeholder labels, fixed semester progress, inactive navigation callbacks |
| Calendar | Seeded 2026–27 dates, month browsing, search, personal events, reminder scheduling | Dates require validation against the college's approved calendar; not cohort configurable |
| Timetable | Dedicated bottom tab, weekday sessions, rooms, status fields | Shared data is unscoped; AI import has insertion and subject-linking defects |
| Subjects | Semester selection, attendance simulation, syllabus and project details backed by Room | Defaults and duplicate attendance logic; incomplete editing workflow |
| Assignments | Local persistence, pending list, submission-status changes | No complete faculty distribution/submission workflow; route exists but is absent from bottom navigation |
| Notices | Firestore-to-Room synchronization and priority filters | No effective publishing UI or class targeting enforcement in client reads |
| AUMS | Interactive portal page and HTML attendance extraction | Fragile parser; scheduled background worker does not fetch attendance |
| AI | Persistent chat display, local query routing, remote generation, attachment sending, data actions | Direct changes without import review; some tools are simulations; prior chat is not sent as conversation history |
| Notifications | Event alarm and periodic briefing plumbing | Briefing contents are hardcoded; scheduling and recovery need completion |

The bottom navigation is Home, Calendar, Timetable, Subjects, and Notices. AI opens in a floating bottom sheet. A separate WeekScreen exists but is not registered in the active navigation graph.

## Most consequential findings

### 1. Shared data is not scoped for multiple student groups

`data/AcnRepository.kt` reads the entire `class_sessions` and `announcements` collections. `saveTimetable()` deletes all documents in the timetable collection before writing replacements. `deleteAllAnnouncements()` similarly attempts global deletion.

`ai/AiToolExecutor.kt` connects `clear_all_notifications` to that announcement deletion without a role check. Clearing a student's notifications must not mean deleting shared notices. Whether deployed Firestore rules allow these requests was not verified: `firebase.json` is empty and no rules files were found in the repository. This is confirmed dangerous client behavior, not proof that the live backend currently permits it.

Required direction: stable college/cohort identifiers, user-owned personal records, independently managed shared content, and server-enforced publishing permissions.

### 2. AUMS login bypasses certificate validation

`ui/screens/AumsLoginScreen.kt` calls `handler?.proceed()` on SSL errors, enables mixed content, and permits unrestricted navigation with a JavaScript bridge installed. The manifest allows cleartext traffic app-wide. This is a release blocker for a screen handling college login sessions.

The attendance parser also guesses numeric column positions: a row containing a serial number before total/attended values can be interpreted incorrectly. Taking the maximum/minimum of two numbers hides ambiguity instead of detecting a bad import. Real anonymized page fixtures and header-based parsing are needed.

### 3. AI timetable import can report success without adding classes

`ai/AssistantViewModel.kt` handles ADD using `repository.updateSession()`. `data/ClassSessionDao.kt` implements that method with `@Update`, which updates existing rows rather than inserting a new row with ID zero. The imported session also uses `subjectId = 1`, despite looking up a subject.

The prompt supplies Firestore IDs when available, while update/delete parsing expects integer IDs. Local AI session edits are not propagated by these repository methods, and a subsequent Firestore snapshot replaces local sessions. These are separate problems with insertion, identity, and synchronization.

### 4. Several completion messages are simulated

`worker/AumsSyncWorker.kt` passes mock HTML to the parser and returns success. It does not implement the background portal fetch described in comments. `worker/NotificationBriefingWorker.kt` always reports the same assignment, practical, attendance percentage, and notice count.

`simulatePdfImport()` adds a chat message claiming calendar dates were saved, but performs no event writes. Faculty `cancel_class` and `post_announcement` tools return success without carrying out those actions. Attachment submission to Gemini is implemented separately; this does not make the simulated PDF action real.

### 5. Student-facing data can be inconsistent

- Home uses `Subject <id>` and `Subject ID ` labels instead of the available subject names.
- Home's warning boundary is 80%; other attendance implementations use 85% for SAFE. Zero recorded classes appears as 100% in SubjectUi but 0% elsewhere.
- Home defaults to semester day 57 of 90, rather than wiring the repository calculation.
- Home captures time with `remember`; current/next class does not advance simply because time passes. Day queries also capture the weekday at construction.
- Home's section links and Ask AI button receive default empty callbacks. The scaffold's separate AI button is wired.
- Home and Notices hardcode STUDENT despite onboarding offering FACULTY.
- Older announcement labels say `d ago` without the number.
- Many screens hardcode dark colors, and the assistant and main UI use separate preference-state instances, so theme updates are not consistently reactive.

### 6. Persistence needs explicit ownership and recovery

Firestore snapshots clear then reinsert local tables without a transaction. Local auto-generated subject IDs are sent as cross-device references, while subjects are not synchronized alongside shared sessions. The database uses destructive migration fallback, so a future schema upgrade without migrations can erase stored data.

Background operations frequently use independent coroutine scopes, and failures mostly print stack traces instead of exposing pending/failed sync state. Database seeding uses GlobalScope and reads the singleton inside an asynchronous callback. Replace these patterns with controlled initialization, transactions, stable identities, migrations, and observable sync status.

### 7. AI needs a narrower, reliable execution boundary

Model-generated JSON is parsed manually and may immediately edit/delete data. Timetable/calendar imports need a parsed preview and validated changes before application. Role checks are selective and rely on a user-selected local preference. Attachment size/type handling, route allowlisting, valid attendance counts, stable identifiers, and transaction boundaries need attention.

Chat is stored locally but remote generation receives the current request and selected app state, not previous turns. Local queries are also blocked by the API-key check even though their execution does not require a remote model. API-key storage attempts encryption but falls back to ordinary SharedPreferences on error. The configured model's live availability was not checked.

## Proposed first student release

Start with one confirmed campus and a small set of real classes. This keeps the academic data verifiable while establishing a model that supports additional departments.

1. Student setup: name, department/programme, batch, semester, section, and recoverable identity. Faculty permissions should be assigned through a verified process.
2. Today: correct class names, room, ongoing/next class, upcoming tasks and exams, and attendance alerts calculated consistently.
3. Timetable/calendar: verified academic dates, class-specific schedules, and dated exceptions for cancellations, holidays, and replacement classes. Weekly templates alone cannot represent these reliably.
4. Subjects: accurate attendance with source and last-sync time, syllabus, projects, and personal tasks. Clearly distinguish official attendance from personal estimates.
5. Notices: authorized publishing with relevant audiences, expiry, and per-student read/dismiss state.
6. Reliability: offline cached reads, recoverable writes, migration safety, clear errors, and useful empty states.
7. AI: reliable read-only questions first, then reviewed imports and validated personal actions. Core student workflows should work without a personal AI API key.

Marks/CGPA, learning-resource libraries, LMS submissions, and faculty administration would be additional scope: no complete implementations were identified. Confirm their priority before expanding the first release.

## Implementation order and acceptance criteria

First repair login handling, global deletion paths, timetable insertion/identity, mock-success behavior, and data migration strategy. Then establish cohort ownership and verified roles. Complete direct screen workflows, consistent calculations, and real data import before expanding AI features.

Validate with two students in different sections, unauthorized publishing attempts, an imported timetable surviving restart/resync, attendance rows with serial numbers and reordered columns, no-data attendance, midnight/class transitions, offline updates, and a database upgrade preserving records. Add a small number of meaningful tests for these behaviors rather than counting template tests as coverage.

## Verification scope

This assessment is grounded in the checked-out source and build configuration. It does not claim a live AUMS login, backend rules audit, AI API validation, or on-device visual walkthrough. Existing screenshots/logs cannot establish the current runtime condition.

Only template unit/instrumentation tests were found. The instrumentation test still asserts the old `com.example.amritacalendar26_27` package, while the app ID is `in.ac.amrita.chen.acnplanner`.

`gradlew.bat testDebugUnitTest assembleDebug --console=plain` completed successfully. All 43 actionable tasks were up-to-date, so this verified the incremental build state and reused existing unit-test results; it was not a fresh clean compilation or fresh test execution. Gradle reported deprecated Android build options and legacy variant API usage. No instrumentation tests were run.
