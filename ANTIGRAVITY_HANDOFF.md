# ACN Planner — implementation handoff

The user has assigned implementation to Antigravity and verification/fixes to Codex to conserve Codex credits. Continue implementing the user's approved connected-courses, groups and reviewed-agent plan. Do not restart the app or overwrite the current working tree. No commit or production deployment has been made by Codex.

## Start here

1. Read the approved plan in your conversation and inspect `git diff` plus untracked files. Much of the implementation already exists; untracked files are essential work, not disposable scaffolding.
2. Read `APP_ASSESSMENT.md`, the new `workspace` package, backend source and rules.
3. Run the build and existing tests before further changes. Finish the outstanding workflows and verify them; do not replace working features with mocks.
4. Keep a concise `ANTIGRAVITY_RESULTS.md` with changed files, exact test commands/results, known limitations and deployment status. Tell the user when ready for Codex review.

## Existing implementation

- Kotlin/Compose/Room retained. Main entry is `app/src/main/java/acn/amrita/chen/planner/workspace/WorkspaceApp.kt`; `MainActivity.kt` launches it.
- Five destinations: Today, Subjects, Planner, Groups, Inbox; Settings and contextual assistant.
- Course workspace defaults to Full Syllabus, with assessment/project/resource/attendance sections, manual editing, personal marks/progress, topic coverage and reminders.
- Room v8 adds owner-scoped records and an outbox. Explicit 7→8 migration preserves the original tables. No destructive fallback. Explicit legacy import copies records privately, queues cloud writes, preserves topic completion and offers a repair route for invalid mappings.
- Verified email accounts; groups with expiring invitation codes, approval, roles, chat/replies/reactions/pins, attachments, course mapping, review queues, mute and unread counts.
- Selected-source Gemini relay with encrypted device BYOK, structured output schema, source evidence, editable proposals, approval, revision checks and idempotency. Private agent context can read approved enrolled group courses; shared extraction excludes private notes/marks.
- Source inspection, revision history/restoration, Inbox local/server conflict comparison, periodic network-constrained outbox delivery and best-effort reminders.
- My Amrita WebView restricts HTTPS origin, cancels certificate errors and previews validated header-based attendance imports. Old mock sync and unsafe global cloud synchronization were disabled. The old direct-mutating AI executor was removed.
- Firebase backend: `functions/src/{index,domain,ai-schema}.ts`, `firestore.rules`, `storage.rules`, `firebase.json`, indexes and test suites. Includes membership/publishing permissions, upload reservations/quotas, AI lease/daily quotas, FCM and configuration kill switches.

## Build and test commands (PowerShell)

From repository root:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest connectedDebugAndroidTest --console=plain
```

From `functions` (prefer Node 22; this machine has run tests with Node 24):

```powershell
npm.cmd ci
npm.cmd test
npm.cmd run test:rules
$env:FUNCTIONS_DISCOVERY_TIMEOUT='60'
npm.cmd run test:integration
```

Run emulator suites sequentially: they share ports. They use `demo-acn-planner`, not production. Functions discovery needed the 60-second environment setting on this Windows machine.

Previously passed: Android assembly/unit tests; three Android instrumented tests (migration, actual manual course/syllabus flow, app package test); seven backend domain tests; four Firestore/Storage rules tests; two callable integration scenarios covering membership/publishing lifecycle, retry deduplication, postponement conflict and private proposal isolation. **There have been additional edits since these full runs. Rerun them on your final tree.** Latest Codex edit fixes a Kotlin generic inference error in the attachment list; `compileDebugKotlin` is being checked at handoff.

The headless read-only Android emulator started by Codex is `emulator-5554` (`Small_Phone`). ADB: `%LOCALAPPDATA%/Android/Sdk/platform-tools/adb.exe`. Application ID: `in.ac.amrita.chen.acnplanner`; activity: `acn.amrita.chen.planner.MainActivity`. Instrumented tests may uninstall the APK. Reinstall before manual inspection. Screenshots in `artifacts` are intermediate, not final release evidence.

## Priority follow-up review and implementation

- Run compile/tests first; latest source/history, manual attachment, shared AI context, unread and notification changes need regression coverage.
- Audit end-to-end legacy import, preserving old assignments/project status/deadlines/chat and account isolation. The underlying old tables remain intact, but preservation of every field in the new UI still needs verification.
- Review outbox recovery races: a newer local edit during conflict review must not be applied without reviewing that exact local version. Preserve causal order and idempotency. Handle invalid/removed-group pending operations without permanently blocking unrelated work.
- Chat pagination currently replaces the live newest page and can discard previously loaded older messages on a snapshot. Fix page retention and test retries/edits/deletions.
- Ensure the shared course mapping works for private study plans and coverage validation. Private records referencing an enrolled shared course are supported; coverage topic lookup across mapped shared syllabi needs further attention.
- Complete proposal editing for every supported field (projects/milestones, timetable times, attachment selection, reminders); show readable before/after comparisons. Keep shared and private actions distinct. Agent-proposed private marks/progress actions are not yet a complete typed capability.
- Audit permissions inside transactions, including revoked-member retries. A prior operation replay now checks current publishing membership. Test that regression. Check syllabus dependent coverage invalidation also writes complete revision history.
- Check dates/semester transitions, holiday/exception behavior, custom attendance requirement, notification clicks, cancellation, offline queues, key failure and upload recovery. Old source code still contains unused legacy features; do not accidentally reconnect mock workers.
- Inspect dark/light appearance, status bar contrast, long names, large text, keyboard/scroll behavior and TalkBack. Course dropdowns should distinguish identical names by course code/semester. Current UI is functional Material Compose, not fully polished.
- Add meaningful tests for the remaining required scenarios in the approved plan, especially authenticated multi-device workflows, source edits after proposals, prompt-like attachment content and revoked access.
- Finish setup/deployment documentation and an honest feature/test status table. Do not claim production or live Gemini/portal validation based on emulator tests.

## Cloud and security boundaries

Configured Firebase project: `acn-planner-app-8821`, region `asia-south1`. Firebase CLI authentication exists, but access/billing/deployment readiness for that exact project has not been fully established. **No production functions or rules have been deployed.** Email/password enablement, Storage/billing, App Check registration/Play Integrity and release configuration still need checking. The new rules intentionally deny legacy global collections; assess existing-client impact before a live rules cutover.

Never log API keys, portal cookies/passwords, message/document bodies or provider request bodies. Keys stay encrypted on device and transient in relay memory. No background AI jobs. AI output cannot execute tools or publish itself. User review is mandatory for mutations. Group membership and publishing permissions are server-enforced. Private imports default private. Do not invent dates/coverage, simulate successful cloud calls, or clear collections during synchronization.

Give the user a working build and a precise status report when implementation is ready. Codex will then inspect your diff, run targeted verification and correct concrete defects rather than reimplementing the app.
