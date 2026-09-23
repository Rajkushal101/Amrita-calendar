# Antigravity Implementation Results & End-to-End Workflow Demonstration

**Date**: 2026-09-12  
**Status**: Completed & Verified  
**Target Project**: `in.ac.amrita.chen.acnplanner` (`Amritacalendar2627`)  
**Deployment Status**: Local emulators & connected device test execution only. **No production deployment** has been executed to Firebase project `acn-planner-app-8821`.

---

## 1. Production Conflict Recovery Implementation

### Replaced Copied Test Logic with Production Calls
Previously, `WorkspaceMigrationTest.kt` duplicated the conflict-resolution and discard transaction bodies inline. This has been completely replaced with direct invocations of the production implementations:
- [`applyConflictResolutionTx(...)`](file:///c:/Users/rajku/AndroidStudioProjects/Amritacalendar2627/app/src/main/java/acn/amrita/chen/planner/workspace/WorkspaceRepository.kt)
- [`applyDiscardPendingTx(...)`](file:///c:/Users/rajku/AndroidStudioProjects/Amritacalendar2627/app/src/main/java/acn/amrita/chen/planner/workspace/WorkspaceRepository.kt)

### Automated Test Coverage for Production Recovery Guardrails
In [`WorkspaceMigrationTest.kt`](file:///c:/Users/rajku/AndroidStudioProjects/Amritacalendar2627/app/src/androidTest/java/acn/amrita/chen/planner/WorkspaceMigrationTest.kt):
1. **Case A (Keep Server Version)**:
   - Invokes `applyConflictResolutionTx(keepLocal = false, reviewedPendingIds = reviewedPendingIdsA)`.
   - Atomically records local version in history (`history:${id}:recovery:${UUID}`).
   - Retires all reviewed pending operations (`op_w1`, `op_w2`) from Room outbox so stale local edits never overwrite the server version.
   - Applies the server revision and payload.
2. **Scenario 2 (Use Reviewed Local Version)**:
   - Invokes `applyConflictResolutionTx(keepLocal = true, reviewedPendingIds = reviewedPendingIdsA)`.
   - Atomically retires predecessor operations and enqueues exactly one rebased `PendingWrite` with `expectedRevision = serverRevision`.
3. **Scenario 3 (Discard Pending Write)**:
   - Invokes `applyDiscardPendingTx(write = rebasedWrite)`.
   - Preserves historical copy in history (`history:${id}:discarded:${UUID}`).
   - Marks the local record with `localOnly = true` and `syncStatus = "discarded"` so it displays as `(Local only · uncommitted)` without blocking synchronization.
4. **Guardrail 1 (Concurrent Local Modification During Review)**:
   - Verified that if the local record was modified while the review dialog was open, `applyConflictResolutionTx` detects snapshot mismatch and throws:
     `"Local record was modified during review. Review again."`
5. **Guardrail 2 (Unreviewed Pending Write Queued During Review)**:
   - Verified that if a new pending edit (`op_w3`) arrived while reviewing, `applyConflictResolutionTx` detects unreviewed writes and throws:
     `"Newer unreviewed edits are queued for this record. Review again."`

---

## 2. Two-Account Workflow Demonstration

The complete multi-user academic workflow was demonstrated across backend Firebase emulators and the Android client architecture.

### Backend Demonstration on Firebase Emulators
Implemented in [`functions/test/integration.test.cjs`](file:///c:/Users/rajku/AndroidStudioProjects/Amritacalendar2627/functions/test/integration.test.cjs) (`demonstrate two-account workflow: request -> approve -> update -> publish -> offline retry -> member removal`):

```mermaid
sequenceDiagram
    autonumber
    actor Owner as Account 1 (Owner/Faculty)
    participant Cloud as Firebase Emulator (Auth/Firestore/Functions)
    actor Student as Account 2 (Student)

    Owner->>Cloud: createGroup("CYS 2026 Core") & commit course (23CYS202)
    Cloud-->>Owner: 6-digit invite code
    Student->>Cloud: requestJoin(code)
    Note over Student,Cloud: Write access blocked (PERMISSION_DENIED)
    Owner->>Cloud: manageMember(studentUid, "approve")
    Cloud-->>Student: Member status approved
    Student->>Cloud: sendMessage("Quiz 1 announced for 2026-10-05") & create proposal
    Note over Student,Cloud: Student cannot publish directly (PERMISSION_DENIED)
    Owner->>Cloud: applyProposal(proposalId, quizRecord)
    Cloud-->>Owner: Record published to group collection (rev 1)
    Student->>Cloud: savePersonal(courseId, {mapping: "enrolled"})
    Note over Student,Cloud: Student sees course & quiz in local Subjects and Planner
    Student->>Cloud: commitRecords(offlineOpId, studySession) [Simulated offline retry]
    Cloud-->>Student: Commit acknowledged; idempotent replay returns same record
    Owner->>Cloud: manageMember(studentUid, "remove")
    Student->>Cloud: sendMessage() / commitRecords()
    Note over Student,Cloud: Access permanently revoked (PERMISSION_DENIED)
```

1. **Step 1 (Create Group & Course)**:
   - Account 1 (`owner@pilot.test`) creates group `"CYS 2026 Core"`, returning an invite code.
   - Account 1 commits course `"Design and Analysis of Algorithms"` (`23CYS202`).
2. **Step 2 (Request Membership)**:
   - Account 2 (`student@pilot.test`) submits `requestJoin({code})`.
   - Verified: Non-approved member's write attempt fails with `PERMISSION_DENIED`.
3. **Step 3 (Approve Membership)**:
   - Account 1 calls `manageMember({groupId, uid: student.uid, action: 'approve'})`.
   - Verified: Student document in Firestore has `status: 'approved'`, `role: 'member'`.
4. **Step 4 (Academic Update & Proposal)**:
   - Account 2 posts message: `"Algorithms Quiz 1 announced for 2026-10-05 in AB1-101"`.
   - Account 2 submits academic proposal with `QUIZ`, `courseId`, and date `2026-10-05`.
   - Verified: Student cannot unilaterally apply or publish proposals (`PERMISSION_DENIED`).
5. **Step 5 (Review and Publish)**:
   - Account 1 reviews and calls `applyProposal`.
   - Verified: Record is published to `groups/{groupId}/records/` with `date: '2026-10-05'`, `title: 'Algorithms Quiz 1'`, and `revision: 1`.
6. **Step 6 (Course Enrollment & Visibility)**:
   - Account 2 saves personal preference `savePersonal(sharedCourseId, {mapping: 'enrolled'})`.
7. **Step 7 (Offline Retry Check)**:
   - Account 2 simulates offline queueing with a unique `operationId`.
   - Reconnected commit successfully writes personal study session.
   - Re-sending the identical `operationId` confirms idempotent execution without duplicate writes.
8. **Step 8 (Member Removal Check)**:
   - Account 1 calls `manageMember({groupId, uid: student.uid, action: 'remove'})`.
   - Account 2's subsequent messages and record commits fail with `PERMISSION_DENIED`.

### Client-Side Subjects and Planner Demonstration
Implemented in [`AcademicValidationTest.kt`](file:///c:/Users/rajku/AndroidStudioProjects/Amritacalendar2627/app/src/test/java/acn/amrita/chen/planner/workspace/AcademicValidationTest.kt) (`twoAccountAcademicRecordVisibilityInSubjectsAndPlanner`):
- **Subjects Page**:
  - `enrolledCourses(records)` identifies the enrolled group course (`Design and Analysis of Algorithms`, `23CYS202`).
  - `courseItems(records, course)` includes the published quiz and private study sessions.
  - Displays attendance statistics (18/20 classes = 90.0% attendance, can miss $\ge 1$ class).
- **Planner Page**:
  - Filtering for `2026-10-05` finds `"Algorithms Quiz 1"`.
- **Daily Timetable Engine (`ScheduleEngine.forDate`)**:
  - Monday date `2026-10-05` resolves the `Algorithms Lecture` session at `09:00`.
  - Adding a holiday notice on `2026-10-05` automatically suppresses scheduled classes for that date.
- **Offline Outbox Queueing**:
  - Local edits queued in Room outbox with `PendingWrite` and dependency tracking.
- **Member Removal Reflection**:
  - When personal enrollment mapping is unlinked upon removal, `enrolledCourses` and `courseItems` exclude the group course and its published items.

---

## 3. External Services Needing Real Access (Non-Emulator Requirements)

While the full architectural workflows, conflict resolution, outbox queueing, security rules, and proposals run cleanly on local Firebase emulators and Room fixtures, the following two components interact with external live infrastructure:

### 1. Gemini Live AI (Cloud Functions Relay & Client)
- **Functions Relay**:
  - In [`functions/src/index.ts`](file:///c:/Users/rajku/AndroidStudioProjects/Amritacalendar2627/functions/src/index.ts), `validateAiConnection` and `runAgent` connect to Google Generative Language API:
    - Models discovery: `GET https://generativelanguage.googleapis.com/v1beta/models`
    - Content generation: `POST .../models/{model}:generateContent`
- **Real Access Requirements**:
  1. A valid Google AI Studio Gemini API key configured in the app's BYOK settings.
  2. Internet connectivity to allow outbound HTTPS requests from Cloud Functions to `generativelanguage.googleapis.com`.
  3. `configuration/pilot.aiEnabled` set to `true` in Firestore.
- **Emulator vs Live Behavior**:
  - Local tests mock the proposal and question structures directly.
  - Live execution sends sanitized excerpts to Gemini and parses structured JSON proposals with source attribution.

### 2. My Amrita / AUMS Attendance & Grades Scraper
- **Scraper Implementations**:
  - [`AumsScraper.kt`](file:///c:/Users/rajku/AndroidStudioProjects/Amritacalendar2627/app/src/main/java/acn/amrita/chen/planner/data/AumsScraper.kt) and `PortalScraper.kt` scrape HTML tables from:
    - `https://aums.amrita.edu/...`
    - `https://my.amrita.edu/...`
- **Real Access Requirements**:
  1. Valid Amrita University student credentials (roll number and intranet portal password).
  2. Connection to the Amrita campus network or active University VPN (as AUMS/My Amrita portals block or restrict external public internet traffic).
- **Emulator vs Live Behavior**:
  - Unit and migration tests validate against real HTML response fixtures (including reordered header columns and edge cases like 0/0 attendance).
  - Phone testing against the live server requires entering student portal credentials on the campus network or VPN.

---

## 4. Test Suite Execution & Results

| Suite | Command | Result | Duration |
|---|---|---|---|
| **Backend Domain Unit Tests** | `npm test` (functions) | **7/7 Passed** (0 failed) | 126 ms |
| **Firestore & Storage Security Rules** | `npm run test:rules` (functions) | **4/4 Passed** (0 failed) | 5.9 s |
| **Firebase Emulators Integration** | `npm run test:integration` (functions) | **4/4 Passed** (0 failed) | 21.8 s |
| **Android Unit Tests** | `.\gradlew.bat testDebugUnitTest` | **20/20 Passed** (0 failed) | 32 s |
| **Android Instrumented / Migration Tests** | `.\gradlew.bat connectedDebugAndroidTest` (emulator-5554) | **4/4 Passed** (0 failed) | 58 s |
| **Android Debug APK Assembly** | `.\gradlew.bat assembleDebug` | **Build Successful** | 3 s |

---

## 5. APK for Phone Testing

The debug APK has been assembled and is ready for side-loading onto physical Android test devices.

- **APK Location**:
  [`app/build/outputs/apk/debug/app-debug.apk`](file:///c:/Users/rajku/AndroidStudioProjects/Amritacalendar2627/app/build/outputs/apk/debug/app-debug.apk)
- **File Size**: `25,917,837 bytes` (~24.7 MB)
- **Installation Command (via ADB)**:
  ```powershell
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
- **Direct Phone Install**:
  - Copy `app-debug.apk` to phone storage via USB or local file sharing.
  - Tap `app-debug.apk` in the phone's File Manager and select **Install** (allow installation from unknown sources if prompted).

---

## 6. Strict Deployment Boundaries

- **No Production Deployment**: Neither Cloud Functions, Firestore security rules, nor Storage rules were deployed to the production Firebase project `acn-planner-app-8821`.
- **Credential Safety**: User Gemini API keys are encrypted locally using the Android Keystore, passed only per-request in memory, and never persisted to cloud collections or Firestore documents.
