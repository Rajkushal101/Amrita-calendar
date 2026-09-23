package acn.amrita.chen.planner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import androidx.room.withTransaction
import acn.amrita.chen.planner.data.*
import acn.amrita.chen.planner.workspace.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceMigrationTest {
    @Test fun upgradePreservesLegacyAcademicRecords()= runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="migration-${java.util.UUID.randomUUID()}.db"
        var database=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        try {
            val subjectId=database.subjectDao().insertSubject(Subject(name="Networks",code="23CYS201",faculty="Faculty",semester=3)).toInt()
            val unitId=database.subjectSyllabusDao().insertUnit(SubjectUnit(subjectId=subjectId,unitNumber=1,title="Networking")).toInt()
            database.subjectSyllabusDao().insertTopic(SubjectTopic(unitId=unitId,title="Routing"))
            database.eventDao().insertEvent(Event(title="Existing exam",dateMillis=12345))
            database.close()
            context.openOrCreateDatabase(name,0,null).use {raw->
                raw.execSQL("DROP TABLE workspace_records");raw.execSQL("DROP TABLE workspace_outbox");raw.version=7
            }
            database=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_7_8).build()
            assertEquals("Networks",database.subjectDao().getAllSubjectsSync().single().name)
            assertEquals("Routing",database.subjectSyllabusDao().getTopicsForUnitsSync(listOf(unitId)).single().title)
            database.workspaceDao().put(AcademicRecord("alice",kind="COURSE",payload="{\"title\":\"Private\"}"))
            assertTrue(database.workspaceDao().all("bob").isEmpty())
        } finally {database.close();context.deleteDatabase(name)}
    }

    @Test fun queuedEditChainResolutionAndDiscardInRoom() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "outbox-test-${java.util.UUID.randomUUID()}.db"
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val dao = database.workspaceDao()
            val user = "alice"
            val recordId = "rec_chain"

            // Scenario 1: Initial record at rev 1, with two queued updates expecting rev 1 and rev 2
            val initial = AcademicRecord(user, recordId, "QUIZ", payload = """{"title":"Quiz Initial"}""", revision = 1, updatedAt = 1000L)
            dao.put(initial)

            val w1 = PendingWrite(user, id = "op_w1", operation = "commitRecords",
                payload = org.json.JSONObject().put("changes", org.json.JSONArray().put(org.json.JSONObject().put("id", recordId).put("kind", "QUIZ").put("action", "UPDATE").put("expectedRevision", 1L).put("data", org.json.JSONObject().put("title", "Quiz Edit 1")))).toString())
            val w2 = PendingWrite(user, id = "op_w2", operation = "commitRecords",
                payload = org.json.JSONObject().put("changes", org.json.JSONArray().put(org.json.JSONObject().put("id", recordId).put("kind", "QUIZ").put("action", "UPDATE").put("expectedRevision", 2L).put("data", org.json.JSONObject().put("title", "Quiz Edit 2")))).toString())
            dao.enqueue(w1)
            dao.enqueue(w2)

            val localLatest = AcademicRecord(user, recordId, "QUIZ", payload = """{"title":"Quiz Edit 2"}""", revision = 3, updatedAt = 2000L)
            dao.put(localLatest)

            // Case A: Keep Server version (server at rev 3 with "Quiz Server") - calling production implementation
            val reviewedPendingIdsA = setOf("op_w1", "op_w2")
            val serverRecordA = org.json.JSONObject().put("id", recordId).put("kind", "QUIZ").put("revision", 3L).put("data", org.json.JSONObject().put("title", "Quiz Server"))

            applyConflictResolutionTx(
                db = database,
                dao = dao,
                owner = user,
                target = recordId,
                write = w1,
                latestServer = serverRecordA,
                reviewedLocal = localLatest,
                keepLocal = false,
                reviewedPendingIds = reviewedPendingIdsA
            )

            // Verify: w1 and w2 are BOTH retired!
            val remainingAfterKeepServer = dao.pending(user).filter { recordId in affectedRecordIds(it) }
            assertTrue("Both queued updates must be retired so w2 does not overwrite server", remainingAfterKeepServer.isEmpty())
            assertEquals("Quiz Server", dao.get(user, recordId)?.json()?.getString("title"))
            assertEquals(3L, dao.get(user, recordId)?.revision)
            val historyA = dao.all(user).filter { it.kind == "HISTORY" && it.id.startsWith("history:$recordId:recovery:") }
            assertEquals(1, historyA.size)

            // Scenario 2: Repeat with Use local - calling production implementation
            dao.enqueue(w1)
            dao.enqueue(w2)
            dao.put(localLatest)

            applyConflictResolutionTx(
                db = database,
                dao = dao,
                owner = user,
                target = recordId,
                write = w1,
                latestServer = serverRecordA,
                reviewedLocal = localLatest,
                keepLocal = true,
                reviewedPendingIds = reviewedPendingIdsA
            )

            val pendingAfterUseLocal = dao.pending(user).filter { recordId in affectedRecordIds(it) }
            assertEquals(1, pendingAfterUseLocal.size)
            val rebasedWrite = pendingAfterUseLocal.first()
            val rebasedChange = org.json.JSONObject(rebasedWrite.payload).getJSONArray("changes").getJSONObject(0)
            assertEquals(3L, rebasedChange.getLong("expectedRevision"))
            assertEquals("Quiz Edit 2", rebasedChange.getJSONObject("data").getString("title"))

            // Scenario 3: Discard pending marks record local-only and preserves history - calling production implementation
            applyDiscardPendingTx(
                db = database,
                dao = dao,
                owner = user,
                write = rebasedWrite
            )

            assertTrue(dao.pending(user).isEmpty())
            val finalRecord = dao.get(user, recordId)!!
            assertTrue(finalRecord.json().getBoolean("localOnly"))
            assertEquals("discarded", finalRecord.json().getString("syncStatus"))
            val historyDiscarded = dao.all(user).filter { it.kind == "HISTORY" && it.id.startsWith("history:$recordId:discarded:") }
            assertEquals(1, historyDiscarded.size)

            // Guardrail 1: Local modified during review
            dao.enqueue(w1)
            dao.put(localLatest.copy(updatedAt = 9999L))
            var modifiedCaught = false
            try {
                applyConflictResolutionTx(
                    db = database,
                    dao = dao,
                    owner = user,
                    target = recordId,
                    write = w1,
                    latestServer = serverRecordA,
                    reviewedLocal = localLatest,
                    keepLocal = false,
                    reviewedPendingIds = reviewedPendingIdsA
                )
            } catch (e: IllegalStateException) {
                modifiedCaught = true
                assertTrue(e.message!!.contains("modified during review"))
            }
            assertTrue("Must reject when local modified during review", modifiedCaught)

            // Guardrail 2: Newer unreviewed pending write queued during review
            dao.put(localLatest)
            val w3 = PendingWrite(user, id = "op_w3", operation = "commitRecords",
                payload = org.json.JSONObject().put("changes", org.json.JSONArray().put(org.json.JSONObject().put("id", recordId).put("kind", "QUIZ").put("action", "UPDATE").put("expectedRevision", 3L).put("data", org.json.JSONObject().put("title", "Quiz Edit 3")))).toString())
            dao.enqueue(w3)
            var unreviewedCaught = false
            try {
                applyConflictResolutionTx(
                    db = database,
                    dao = dao,
                    owner = user,
                    target = recordId,
                    write = w1,
                    latestServer = serverRecordA,
                    reviewedLocal = localLatest,
                    keepLocal = false,
                    reviewedPendingIds = reviewedPendingIdsA
                )
            } catch (e: IllegalStateException) {
                unreviewedCaught = true
                assertTrue(e.message!!.contains("Newer unreviewed edits are queued"))
            }
            assertTrue("Must reject when unreviewed pending edit exists", unreviewedCaught)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
