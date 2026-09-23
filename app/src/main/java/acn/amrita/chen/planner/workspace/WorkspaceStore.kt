package acn.amrita.chen.planner.workspace

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.util.UUID

/** Local academic records. Owner is the authenticated UID or the device's legacy profile. */
@Entity(tableName = "workspace_records", primaryKeys = ["owner", "id"])
data class AcademicRecord(
    val owner: String,
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val courseId: String = "",
    val groupId: String = "",
    val payload: String = "{}",
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
) {
    fun json() = JSONObject(payload)
    val title get() = json().optString("title")
}

@Entity(tableName = "workspace_outbox", primaryKeys = ["owner", "id"])
data class PendingWrite(
    val owner: String,
    val id: String = UUID.randomUUID().toString(),
    val operation: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val error: String = ""
)

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspace_records WHERE owner = :owner AND deleted = 0 ORDER BY updatedAt DESC")
    fun observe(owner: String): Flow<List<AcademicRecord>>
    @Query("SELECT * FROM workspace_records WHERE owner = :owner AND id = :id")
    suspend fun get(owner: String, id: String): AcademicRecord?
    @Query("SELECT * FROM workspace_records WHERE owner = :owner")
    suspend fun all(owner: String): List<AcademicRecord>
    @Upsert suspend fun put(record: AcademicRecord)
    @Upsert suspend fun putAll(records: List<AcademicRecord>)
    @Query("DELETE FROM workspace_records WHERE owner = :owner AND groupId = :groupId")
    suspend fun purgeGroup(owner: String, groupId: String)
    @Query("DELETE FROM workspace_records WHERE owner = :owner")
    suspend fun purgeOwner(owner: String)
    @Query("DELETE FROM workspace_records WHERE owner = :owner AND kind = 'CHAT'")
    suspend fun clearChat(owner: String)
    @Upsert suspend fun enqueue(write: PendingWrite)
    @Query("SELECT * FROM workspace_outbox WHERE owner = :owner ORDER BY createdAt ASC")
    suspend fun pending(owner: String): List<PendingWrite>
    @Query("SELECT * FROM workspace_outbox WHERE owner = :owner ORDER BY createdAt ASC")
    fun observePending(owner: String): Flow<List<PendingWrite>>
    @Query("DELETE FROM workspace_outbox WHERE owner = :owner AND id = :id")
    suspend fun acknowledge(owner: String, id: String)
    @Query("DELETE FROM workspace_outbox WHERE owner = :owner")
    suspend fun clearPending(owner: String)
}

enum class AcademicKind { COURSE, SYLLABUS, MIDTERM, END_SEMESTER, QUIZ, ASSIGNMENT, PROJECT, RESOURCE, STUDY_SESSION, TIMETABLE, NOTICE }

/** Validation independent from prompts and UI. Unknown dates remain null. */
object AcademicValidation {
    fun validate(kind: String, data: JSONObject) {
        require(AcademicKind.entries.any { it.name == kind }) { "Unsupported academic section" }
        require(data.optString("title").isNotBlank()) { "A title is required" }
        require(data.optString("title").length <= 200) { "Title is too long" }
        val due = data.optString("date").takeUnless { it.isBlank() || it == "null" }
        due?.let { java.time.LocalDate.parse(it) }
        data.optString("time").takeIf { it.isNotBlank() && it != "null" }?.let { java.time.LocalTime.parse(it) }
        if (kind == "COURSE") {
            require(data.optString("code").isNotBlank()) { "Course code is required" }
            require(data.optInt("semester") in 1..12) { "Semester must be 1–12" }
        }
        if (kind == "RESOURCE" && data.optString("url").isNotBlank()) {
            require(java.net.URI(data.getString("url")).scheme == "https") { "Resources must use HTTPS" }
        }
        if (kind == "TIMETABLE") {
            require(data.optInt("day") in 1..7) { "Day must be 1–7" }
            require(java.time.LocalTime.parse(data.getString("endTime")).isAfter(java.time.LocalTime.parse(data.getString("time")))) { "End time must follow start time" }
        }
    }

    fun attendance(attended: Int, total: Int, required: Int = 75): AttendanceSummary {
        require(total >= 0 && attended in 0..total && required in 1..99)
        if (total == 0) return AttendanceSummary(null, 0, 0, "No classes recorded")
        val pct = attended * 100.0 / total
        val miss = ((attended * 100L / required) - total).coerceAtLeast(0).toInt()
        val needed = kotlin.math.ceil((required * total - 100.0 * attended) / (100 - required)).toInt().coerceAtLeast(0)
        return AttendanceSummary(pct, miss, needed, when { pct >= 85 -> "Safe"; pct >= required -> "Watch attendance"; else -> "Below requirement" })
    }
}

data class AttendanceSummary(val percentage: Double?, val canMiss: Int, val needed: Int, val label: String)
