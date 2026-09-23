# Follow-up review — three reproducible gaps remain

Codex checked current source and tests. The reported passing suites do not cover the following production paths. Fix these narrowly; no redesign or deployment is requested.

## 1. Test and support the mapping actually written by the Groups screen

`GroupWorkspace.kt` writes `savePersonal(id = sharedCourse, data.mapping = privateCourse)`. Backend `applyChanges` and the new Form B integration test instead expect `savePersonal(id = privateCourse, data.mapping = sharedCourse)`. Therefore the new test passes for a mapping the Groups screen does not create, while the student's real mapped assessment can still fail coverage validation.

Use the existing shared→private mapping consistently (or migrate all readers/writers explicitly). Test via the exact `savePersonal` request the Groups screen emits; then save a private assessment with shared coverage. Also verify membership removal. Do not seed the reverse mapping solely to make the test pass. Move `const topics = new Set<string>()` before any branch using it: the non-shared targetMapping branch currently calls `topics.add` before initialization.

## 2. Resolve queued edit chains, not just their first entry

`resolveConflict` now acknowledges only `write.id`, but it restores/rebases the latest local snapshot, which may already incorporate several queued edits. Older queued edits remain ahead of the replacement operation and can overwrite the selected version or immediately conflict again.

Reproduce with server revision 3 and two queued updates expecting revisions 1 and 2. Review the latest local payload and choose Keep server. The second pending update must not later write the discarded local values. Repeat with Use local: the reviewed value should publish exactly once with the correct revision and no stale predecessor remaining. Capture the exact set of pending operations represented by the review; atomically retire only that reviewed set and preserve/reject any newer unreviewed edits. Update the dialog wording to match the behavior. Test the real Room transaction/outbox path, not just snapshot equality.

`discardPending` also only deletes the queue entry while leaving the local optimistic record apparently saved. Define and display the resulting state honestly; preserve a recoverable copy and reconcile the displayed record or mark it explicitly local-only.

## 3. Persist structured retry classifications

Terminal errors are classified by Firebase exception code during the first attempt, but only `safeError(e)` human-readable text is persisted. Future retry suppression scans that text for code names. For example, INVALID_ARGUMENT with message `Attachment does not belong to this workspace` loses its code and is resent every automatic cycle. Conflict text prefixed by transport formatting can similarly evade `startsWith("conflict:")`.

Persist a machine-readable classification/code (a backward-compatible encoded prefix is sufficient if avoiding a migration). Use that field for automatic retry policy. Test the complete exception→persisted write→next retry path using representative backend messages. Do not permanently classify expired authentication as bad user data.

Return a brief report with the three failing-before/passing-after regression scenarios and exact test results. Run backend integration for the real mapping, Android repository/instrumentation for queued chains, and relevant existing suites. Preserve all existing work; no `git checkout`/reset of working files.
