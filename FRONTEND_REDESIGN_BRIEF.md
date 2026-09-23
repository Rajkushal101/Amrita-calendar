# ACN Planner — complete frontend redesign

The user requests a complete frontend redesign before phone-pilot/backend activation. Antigravity implements; Codex reviews the result. Retain the working Kotlin/Compose application and academic workflows. No new plugins are required.

## Visual direction

Create a cohesive, polished university companion: solid surfaces, confident typography, restrained crimson accents, practical academic information and comfortable touch targets. Avoid a generic chat dashboard, excessive pills, glass effects, decorative gradients and oversized empty cards. Establish hierarchy through layout and typography rather than coloring every panel.

The user supplied the myAmrita brand sample: **#BF0C4E**, RGB **191, 12, 78**. Treat this as the supplied brand color, not a verified complete institutional brand guide. Do not invent university logos or official affiliations. Retain an existing appropriate app logo if available.

## Shared design system — implement first

- Centralize colors, typography, dimensions and reusable components in a dedicated Compose theme/components package. Remove scattered screen-specific colors.
- Light theme: brand/primary `#BF0C4E`, on-primary white; warm background `#FAF7F8`; white card surfaces; pale crimson selected surfaces `#FCE7EE`; main text `#281E23`; secondary text `#695A62`; subtle borders `#E8DDE2`.
- Dark theme: background `#171216`; surface `#231B20`; raised surface `#2E242A`; main text `#F7EDF2`; secondary text `#CDBDC5`; light crimson primary `#FF9AB8` with dark on-primary. Keep the supplied crimson for brand accents where contrast permits. Do not put dark crimson body text on dark surfaces.
- These are starting tokens: verify actual foreground/background contrast, aiming for WCAG AA. State colors for success, caution and error must include text/icons rather than color alone.
- Use the existing font or system sans-serif with a deliberate scale: screen titles 26–30sp, section headings 20–22sp, card titles 16–18sp, body 14–16sp, supporting labels 12–13sp. Use sp and allow font scaling; do not force all sizes or truncate essential content to fit a mockup.
- Spacing rhythm: 4/8/12/16/24/32dp; screen horizontal padding 20dp; minimum interactive targets 48dp. Cards around 16dp radius, controls 10–12dp, dialogs around 20dp. Reserve pill shapes for small chips/badges.
- Use consistent Material icons. Align icon sizes, baselines, dividers, content widths and vertical spacing. Prefer subtle outlines or tonal separation to heavy shadows.
- Build reusable screen headers, course cards, information rows, status badges, empty states, loading states, error banners, form fields, section selectors and primary/secondary buttons.
- Maintain persistent light/dark preference, legible system bars, keyboard insets and comfortable layouts on small phones and tablets.

## Redesign all destinations and flows

### Today

Compact branded header, greeting/date, clear next/current class card, upcoming deadlines, attendance alerts and recent relevant changes. Prefer useful compact rows over many large identical empty-state cards. Provide a clear route to the study planner. Do not fabricate dashboard values or sample records in production.

### Subjects

Semester selector and Sync action with truthful last-sync state. Course cards show title, code, attendance state and nearest deadline with strong visual hierarchy. Long names wrap. Never render unknown attendance as 100%.

### Full-screen course workspace

Back navigation and course identity, then a horizontally scrollable section selector. Full Syllabus remains the default. Redesign Midterm, End Semester, Quizzes, Assignments, Projects, Resources and Attendance consistently. Units should be clearly grouped; topics use accessible completion controls. Assessments show dates, announced coverage, resources and private progress distinctly. Use a clear private/shared badge and unobtrusive source/history actions. Project milestones should form a readable checklist/timeline. Preserve all editing, simulation and progress workflows.

### Planner

Consistent Calendar / Week / Tasks switching; clear selected/today dates and readable day agenda. Week sessions show time, course and room. Assessments and private study sessions are distinguishable with labels. Handle holidays and exceptions truthfully. Forms should make date/time selection convenient while preserving validation and unknown-date states.

### Groups

Group list with clear unread counts, membership state and create/join controls. Within a group, redesign Chat, Files, Course Updates, Review Queue and Members. Chat uses compact sender/time/reply information, recognizable own/other message surfaces, attachment cards, selection feedback and accessible sending/failed states. Keep the composer anchored above the keyboard rather than buried below the entire message history. Retain pagination and all role/membership controls.

### Inbox and review

Organize pending reviews, sync issues and change history so actions are easy to find. Proposal review must show readable before/after fields, destination, sources and questions. **Do not display raw JSON payloads as the primary user-facing conflict/version review.** Clearly separate approve, reject, retry, resolve and discard. Preserve the exact reviewed-snapshot and outbox guards.

### Assistant

A consistent, unobtrusive assistant entry point on every applicable app page. Redesign the assistant conversation, attachments, selected-source preview, answer/prepare mode and progress/cancellation states. Make the next review action discoverable. Keep the existing consent and review boundaries; attractive presentation must not imply that a proposal has already been applied.

### Settings, account, onboarding and secondary surfaces

Group settings by Account, Academic profile, AI connection, Appearance, Notifications and Existing data. Give connection/verification states readable labels. Redesign sign-in, verification, key entry, manual editors, personal marks/progress, source viewers, history/restore, invitation/member dialogs, upload states, empty/error states and confirmation surfaces. Keep API keys masked.

Style the app-owned My Amrita toolbar and import preview consistently. Do not inject branding into or modify the university's external login page. Retain origin/certificate restrictions and the import confirmation step.

## Implementation and scope constraints

- This is a frontend redesign, not a fresh app or backend rewrite. Preserve records, routes, IDs, Firebase authorization, key handling, migrations, outbox ordering, source references and mutation approval semantics.
- Review the existing working tree before editing. No destructive resets/checkouts; do not remove a workflow to simplify the design.
- Add no plugin or UI framework merely for styling. Compose/Material3 are sufficient. Use existing icon assets unless a specific new asset is justified.
- Do not deploy backend or production rules during this pass.
- If an existing screen needs a layout/state refactor (especially chat), keep behavior tests and explain the change.

## Delivery and verification

1. Implement the design tokens and reusable components, then apply them across the entire frontend. Do not stop after recoloring the navigation bar or only redesigning Today.
2. Use realistic demo records only in previews or an isolated emulator test account/database. Include long course names, multiple quizzes, project milestones and populated group messages in visual checks.
3. Build and run existing Android unit and instrumented tests. Add targeted tests only for changed interactions or navigation; do not weaken existing assertions to get a pass.
4. Inspect actual rendered screens in light and dark mode, small-screen layouts, keyboard-open chat/forms, large font scale and TalkBack labels. Check bottom-navigation/FAB overlap, clipped text, contrast, touch targets and scrolling.
5. Deliver screenshots of Today, Subjects, a populated course workspace, Planner, Groups/chat, Inbox/proposal review, Assistant and Settings. Capture both themes and representative secondary states. Save them under `artifacts/redesign/`; ensure PNG files are valid (use `adb shell screencap -p` followed by `adb pull` rather than binary redirection through incompatible PowerShell).
6. Produce the updated debug APK and `FRONTEND_REDESIGN_RESULTS.md` listing redesigned surfaces, screenshots, tests and any incomplete areas. Avoid claiming visual verification from compilation alone.

The user should receive a consistent, usable academic app that feels intentionally designed around the supplied college color. Codex will inspect the screenshots and targeted code changes before the phone pilot continues.
