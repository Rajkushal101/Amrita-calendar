# ACN Planner: connected courses, class groups, and a personal academic agent

## 1. Product vision and decisions

Build an Android academic companion where **courses, class conversations, schedules, deadlines, and personal study plans stay connected**.

A student should be able to share a syllabus, select an important group message, or ask the assistant for help—and receive a clear, reviewable proposal that updates the appropriate parts of the app.

Your sketches establish the core experience:

- **Subjects:** semester selector, Sync button, course cards with attendance, and a full-screen course workspace.
- **Groups:** class-group list, conversations, attachments, and membership through an invitation code.
- **Agent:** accessible everywhere, understands the current screen, and prepares actions across the app.

**Decisions confirmed with you**

| Decision | Agreed approach |
|---|---|
| Initial audience | A few classes, with a model that supports more departments later |
| Group membership | Invitation code followed by administrator approval |
| Shared course updates | AI prepares proposals; an administrator or designated publisher approves |
| Personal agent actions | Review every change before applying |
| External conversations | Manual imports of forwarded text, screenshots, files, and chat exports |
| AI access | Students provide their own API key for agent features |
| Initial provider | Gemini, behind an extensible provider interface |
| Key handling | Encrypted device storage; temporary authenticated backend relay |
| Extraction trigger | User selects messages or files and explicitly starts extraction |
| Hosting | Firebase with billing enabled, quotas, and usage controls |
| Academic scope | Planning, syllabus, resources, projects, preparation progress, and private marks |

Students without a key can still use groups, read approved course information, and manage academic records manually. Their AI actions remain unavailable until they connect a key.

## 2. Student experience

### Main navigation

Use five main destinations:

| Page | Purpose |
|---|---|
| **Today** | Current/next class, deadlines, attendance alerts, upcoming assessments, and recent changes |
| **Subjects** | Semester courses and complete course workspaces |
| **Planner** | Calendar, weekly timetable, assignments, and personal study sessions |
| **Groups** | Class conversations, shared files, and academic update proposals |
| **Inbox** | Academic notices, personal review requests, publishing reviews, and change history |

Profile, semester selection, AI connection, notification settings, and privacy controls live in Settings. The assistant opens from every page with relevant screen context.

### Subjects and course workspaces

The Subjects page follows your first sketch:

- Selected semester at the top, with Sync and last successful sync time.
- Course cards showing name, code, attendance, and the nearest relevant deadline.
- Previous semesters remain accessible.
- Attendance with no imported records displays **Not synced** or **No classes recorded**, never a misleading percentage.

Tapping a course opens a **full-screen workspace**, with a back button, course identity, and horizontally scrollable section controls.

| Section | Contents |
|---|---|
| **Full Syllabus — default** | Units, topics, source document, version, and personal completion checkboxes |
| **Midterm** | Announced coverage, date/time, instructions, resources, private marks, and preparation |
| **End Semester** | Its own coverage, schedule, resources, marks, and preparation |
| **Quizzes** | Multiple quizzes, each with coverage, date, instructions, and private results |
| **Assignments** | Multiple tasks, deadlines, attachments, instructions, and personal completion status |
| **Projects** | Multiple projects, milestones, team notes, deliverables, deadlines, and resources |
| **Resources** | Notes, slides, PDFs, links, and previous papers organized by topic or assessment |
| **Attendance** | Imported counts, freshness, configured requirement, and clearly labelled simulations |

“Full Syllabus” is the initial state and the reset destination when a filter is cleared.

**Important behavior**

- Assessment coverage references syllabus topics rather than duplicating syllabus text.
- Unknown coverage displays **Not announced**. AI must not assume that a midterm covers particular units.
- Updating the syllabus preserves earlier versions and flags assessment mappings that require review.
- Shared instructions and dates are separate from private marks, notes, reminders, and preparation progress.
- Personal marks support earned marks and maximum marks. Weighted totals appear only when the grading weights have been explicitly supplied; CGPA prediction is deferred.
- Each important shared fact shows its source, approver, and update time.

### Groups

The Groups page follows your second sketch and adds an academic layer.

**Membership**

- Verified email/password accounts for the pilot, with password recovery.
- Profile contains campus, programme, batch, semester, section, name, and roll number.
- A student creates a group and becomes its owner.
- Invitation codes are generated server-side, expire after seven days, and can be rotated.
- Code entry creates a pending request; only approved members can read group content.
- Roles: owner, administrator, publisher, and member.
- Faculty can be designated publishers by an administrator. This is a group permission, not a claim of institution-verified faculty identity.
- Owners can transfer ownership; administrators can remove members and revoke access.

**Group workspace**

- **Chat:** text, replies, images, files, reactions, and pinned messages.
- **Files:** shared documents with course and assessment tags.
- **Course Updates:** approved academic changes.
- **Review Queue:** proposals awaiting an administrator or publisher.
- **Members:** membership requests, roles, and invitation controls.

Use paginated messages, upload progress, sending/failed states, retry, mute controls, and unread counts. Defer voice/video calling, public discovery, and private direct messaging.

Each student maps a group’s courses to their own semester enrolments. Electives and differently named courses require an explicit mapping instead of matching only by display name.

### My Amrita synchronization

Treat the portal as the source for **enrolled courses and official attendance when those fields are actually available**. Do not assume it supplies syllabus, exam coverage, or assignments.

- Retain interactive login on the student’s device.
- Fix certificate validation and restrict the WebView and JavaScript bridge to approved portal origins.
- Parse recognized table headers and validate counts.
- Show an import summary before saving.
- Preserve existing data when login expires, parsing fails, or the portal returns an empty/error page.
- Record the selected semester and successful sync timestamp.
- Never send portal passwords, cookies, or complete login-page HTML to the AI relay.
- Offer manual course setup when the portal cannot be imported.
- Remove the mock background sync. Unattended portal synchronization is outside the pilot until a supported, tested mechanism exists.

## 3. Turning conversations into academic information

Use one ingestion pipeline for group selections and private uploads:

**Select sources → extract facts → match courses → compare existing records → review changes → apply → synchronize.**

### Group example

A member selects:

> “Networks Quiz 2 is Friday at 10. Units 2 and 3.”

The assistant prepares a proposal containing:

- Matched course and quiz identity.
- Proposed date/time and syllabus coverage.
- Relevant source messages.
- Any unresolved ambiguity.
- Changes to an existing quiz, if one already exists.

An authorized publisher reviews and approves it. The shared quiz then appears in the course workspace and Planner for enrolled group members.

Students receive the same approved record through synchronization; each student does not need to spend another AI request extracting it.

A later postponement modifies the linked quiz through a new reviewed proposal. It does not create a second quiz.

### Private imports

Students can import:

- PDFs and images.
- Pasted or forwarded text.
- Plain-text chat exports.
- Files shared to ACN Planner through Android’s share sheet.

Private imports default to the student’s workspace. Publishing them to a group is a separate action showing the exact information and attachments being shared.

For the pilot, support PDF, JPEG, PNG, WebP, and TXT. Cap individual uploads at 25 MB and AI PDF extraction at 50 pages per request. Show unsupported formats clearly rather than claiming they were processed.

### Review and conflict handling

Every AI proposal displays:

- Proposed additions, edits, and removals.
- Personal or shared destination.
- Supporting message/document references.
- Before/after values.
- Missing fields and conflicts.
- Editable fields and selectable changes.

Apply only the reviewed version. If the underlying record changed meanwhile, return the proposal for another review.

Rules:

- No inferred deadline is silently accepted.
- Ambiguous dates and course matches require correction.
- Duplicate source selections reuse or identify an existing proposal.
- Source edits/deletions mark affected proposals or published records for review; they do not silently rewrite academic facts.
- Private overrides remain separate and visibly labelled.
- Restoring an earlier shared version creates a new audited revision.
- Group messages and documents are untrusted content, never instructions granting the agent new permissions.

## 4. Agent capabilities and implementation

### A personal agent with concrete academic tools

Provide one assistant with specialist capabilities behind it:

| Capability | Examples |
|---|---|
| Academic organizer | Extract syllabus, match resources, prepare course updates |
| Schedule assistant | Find classes, explain clashes, prepare timetable changes |
| Study planner | Build revision sessions from coverage, available time, and deadlines |
| Attendance assistant | Explain imported attendance and simulate future attendance |
| Group assistant | Summarize selected conversations and prepare publishing proposals |
| Progress assistant | Track personal study tasks, project milestones, and entered marks |

The assistant can answer read-only questions immediately. **Academic mutations, reminders, and message sending require review.** One approval may cover a clearly listed batch, such as five study sessions and their reminders.

A study plan asks for missing availability or uses availability explicitly saved by the student. It does not invent free time. If deadlines change, it prepares a revised plan for review.

Show truthful progress: reading sources, extracting, preparing proposal, awaiting review, applied, or failed. Cancelling generation must not apply partial changes.

### Provider and key architecture

Introduce an `AiProvider` interface for generation, structured extraction, capability reporting, and connection validation.

- Ship the Gemini adapter first.
- Select models from supported configuration and validate access; remove the hardcoded model assumption.
- Keep provider-specific request formats outside academic business logic.
- Store keys using Android Keystore-backed encryption, with no plaintext fallback or backup.
- Pass a key to the authenticated relay only for the current request.
- Never persist keys in Firestore, task queues, logs, analytics, or error reports.
- Permit only allowlisted provider destinations.
- No unattended AI jobs in the pilot; the user supplies authorization and a key for each initiated workflow.
- Display which selected content will be sent to the provider.
- Track available token usage and request counts; do not present unverified cost estimates as exact charges.

The relay follows Google’s recommendation to use a backend for production mobile API calls. [Gemini key guidance](https://ai.google.dev/gemini-api/docs/api-key)

Use structured output followed by deterministic validation. A valid JSON response alone is not permission to change records. [Gemini structured outputs](https://ai.google.dev/gemini-api/docs/structured-output)

### Application and backend structure

Retain Kotlin, Compose, and Room. Organize responsibilities into identity, academics, groups, ingestion, agent, and synchronization components, with dependency injection and shared repositories.

Keep Firebase and add:

- Authentication for recoverable accounts.
- Firestore for scoped records and messages.
- Cloud Storage for private and group attachments.
- TypeScript Cloud Functions for membership, privileged mutations, proposal application, and the AI relay.
- FCM for new-message and academic-update notifications.
- App Check plus authorization on backend operations.

Callable functions carry Firebase authentication context, but every operation must still validate membership and permissions. [Firebase callable functions](https://firebase.google.com/docs/functions/callable)

### Core types and interfaces

| Type | Responsibility |
|---|---|
| `AcademicTerm`, `Cohort`, `CourseOffering` | Stable academic identities scoped to campus, term, and student group |
| `Enrollment` | Student-to-course association and portal mapping |
| `SyllabusVersion`, `Unit`, `Topic` | Versioned course content |
| `Assessment`, `CoverageLink` | Exams, quizzes, assignments, and their syllabus coverage |
| `Project`, `Milestone`, `Resource` | Project planning and linked material |
| `PersonalCourseState` | Private progress, scores, notes, and overrides |
| `ClassGroup`, `Membership`, `Message`, `Attachment` | Group communication and access |
| `SourceReference`, `ChangeProposal`, `AcademicRevision` | Evidence, review, and history |
| `AgentRun`, `ActionProposal` | Agent execution status and approved actions |

Use stable UUID/document identifiers across devices. Local integer database IDs must not act as shared course identities.

Backend interfaces include:

- Group creation, join request, membership approval, and role changes.
- Authorized message/attachment creation.
- Source extraction returning a proposal.
- Proposal application using an expected revision and idempotency key.
- Scoped synchronization and notification delivery.

The server validates permissions and data independently of what the AI or client sends.

### Persistence, migration, and operation

- Room remains the observable local store; synchronize records by stable identity.
- Use transactions, an outbox, retries, version checks, and deletion markers.
- Scope personal data by user and shared data by group/course.
- Never clear whole local or shared collections during ordinary synchronization.
- Approved shared updates synchronize automatically; this is delivery of reviewed records, not another AI edit.
- Publish only after the backend acknowledges success.
- Allow cached reading and clearly marked pending operations offline.
- Remove destructive migration fallback and migrate existing version-7 records while preserving relationships.
- Keep existing unscoped cloud records outside the new group model until an administrator explicitly maps them.
- Account changes isolate cached private data.
- Migration preserves existing events, tasks, syllabus, and chat; ambiguous subject mappings enter a repair flow.

Start the pilot with three classes, at most 150 members per group, paginated 50-message reads, ten extraction requests per user per day, and one concurrent extraction per user. Keep these server-configurable.

Budget alerts are notifications, not spending caps. Add application quotas, bounded server concurrency, and a switch to disable uploads or AI relay requests. Firebase Storage requires a billing-enabled project. [Firebase storage requirements](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024)

## 5. Delivery stages and acceptance criteria

| Stage | Deliverable | Completion gate |
|---|---|---|
| **1. Reliable foundation** | Fix login handling, false success messages, timetable writes, subject identity, attendance consistency, migrations, and inactive navigation | Existing personal data survives upgrade; no known global deletion paths remain |
| **2. Course workspaces** | Full-screen syllabus-first course pages, assessments, projects, resources, private progress and marks | A student can complete all core course workflows manually |
| **3. Class groups** | Accounts, approved membership, messaging, attachments, roles, and course mapping | Two different classes remain isolated; authorized publishing works |
| **4. Reviewed extraction** | Private uploads, selected-message extraction, source links, proposals, and versioned application | A source becomes the correct academic record after review, without duplicates |
| **5. Connected agent** | Contextual questions, study planning, attendance explanations, reviewed reminders, and revision proposals | Multi-step actions are understandable, cancellable, and never applied without approval |
| **6. Pilot hardening** | Offline behavior, quotas, accessibility, notification controls, diagnostics, and recovery | Real students complete end-to-end scenarios on multiple devices |

**Required tests**

- A new student requests group access and cannot read messages before approval.
- A member cannot publish or access another class’s files by changing identifiers.
- My Amrita import handles reordered columns, serial numbers, empty tables, expired login, and invalid counts.
- A private syllabus import does not appear in a group.
- Extracting the same quiz twice does not create duplicate records.
- A postponement updates one assessment and preserves its history.
- Conflicting syllabus or deadline proposals cannot overwrite newer records unnoticed.
- Prompt-like instructions inside attachments cannot cause tool execution or data disclosure.
- Revoked keys, rate limits, failed uploads, and cancellation produce recoverable states.
- Offline retries do not duplicate messages or approved changes.
- Group removal revokes future access and removes accessible cached group content on synchronization.
- Midnight, holidays, timetable exceptions, and semester transitions update Today correctly.
- Database upgrades preserve existing records and personal progress.
- Large text, screen readers, light/dark themes, and long course names remain usable.
- Logs and crash reports contain no API keys, portal sessions, or document/message bodies.

**Expansion after the pilot**

Add further provider adapters, richer resource search, practice questions grounded in course material, project collaboration, and institution-supported integrations. Faculty grading/submission workflows, automatic external-chat connections, voice/video calling, and autonomous background AI require separate implementation plans.

The first release succeeds when **a real class can discuss an academic update, approve its extraction once, and see accurate course information across students’ apps—while each student retains control over their own study plan and data.**
