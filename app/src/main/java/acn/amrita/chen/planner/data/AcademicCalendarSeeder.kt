package acn.amrita.chen.planner.data

import android.content.Context
import acn.amrita.chen.planner.workspace.AcademicRecord
import acn.amrita.chen.planner.workspace.WorkspaceDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Seeds the academic calendar for AY2026-27 (Amrita Vishwa Vidyapeetham, Chennai)
 * from the official "Academic Calendar - AY2026-27_V1.0" PDF.
 *
 * Events are stored as AcademicRecord entries in the workspace_records table
 * so they appear in the PlannerPage calendar view and can be edited or managed by AI.
 */
object AcademicCalendarSeeder {

    private const val PREFS_KEY_PREFIX = "academic_calendar_seeded_v3_"

    fun seedIfNeeded(context: Context, dao: WorkspaceDao, owner: String) {
        val prefs = context.getSharedPreferences("acn_prefs", Context.MODE_PRIVATE)
        val key = "$PREFS_KEY_PREFIX$owner"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alreadySeeded = prefs.getBoolean(key, false)
                val existingRecords = dao.all(owner)
                val hasCalRecords = existingRecords.any { it.id.startsWith("acal:") }

                if (!alreadySeeded || !hasCalRecords) {
                    val records = buildAcademicCalendar(owner)
                    dao.putAll(records)
                    prefs.edit().putBoolean(key, true).apply()
                    android.util.Log.i("CalendarSeeder", "Successfully seeded ${records.size} academic calendar events for owner $owner")
                }
            } catch (e: Exception) {
                android.util.Log.e("CalendarSeeder", "Failed to seed calendar for owner $owner", e)
            }
        }
    }

    private fun payload(title: String, date: String, type: String, notes: String = ""): String {
        val j = JSONObject()
        j.put("title", title)
        j.put("date", date)
        j.put("calendarType", type) // "holiday", "exam", "academic"
        if (notes.isNotBlank()) j.put("notes", notes)
        return j.toString()
    }

    fun buildAcademicCalendar(owner: String): List<AcademicRecord> {
        val records = mutableListOf<AcademicRecord>()

        fun holiday(date: String, title: String, notes: String = "") {
            records.add(AcademicRecord(
                owner = owner,
                id = "acal:holiday:$date",
                kind = "NOTICE",
                payload = payload(title, date, "holiday", notes),
                deleted = false
            ))
        }

        fun exam(date: String, title: String, kind: String = "MIDTERM", notes: String = "") {
            records.add(AcademicRecord(
                owner = owner,
                id = "acal:exam:$date:${title.hashCode()}",
                kind = kind,
                payload = payload(title, date, "exam", notes),
                deleted = false
            ))
        }

        fun academic(date: String, title: String, notes: String = "") {
            records.add(AcademicRecord(
                owner = owner,
                id = "acal:acad:$date:${title.hashCode()}",
                kind = "NOTICE",
                payload = payload(title, date, "academic", notes),
                deleted = false
            ))
        }

        // ═══════════════════════════════════════════════════════════════
        // HOLIDAYS — Official Staff & Student Holidays
        // ═══════════════════════════════════════════════════════════════

        // June 2026
        holiday("2026-06-26", "Muharram")

        // August 2026
        holiday("2026-08-15", "Independence Day")
        holiday("2026-08-26", "Onam Sadhya / Milad-un-Nabi")

        // September 2026
        holiday("2026-09-04", "Krishna Janmashtami")
        holiday("2026-09-14", "Vinayakar Chaturthi")
        holiday("2026-09-27", "Amma's Jayanthi")

        // October 2026
        holiday("2026-10-02", "Gandhi Jayanthi")
        holiday("2026-10-19", "Ayutha Pooja")
        holiday("2026-10-20", "Vijaya Dasami")

        // November 2026
        holiday("2026-11-08", "Deepavali")
        holiday("2026-11-09", "Deepavali Holiday")

        // December 2026
        holiday("2026-12-25", "Christmas")

        // January 2027
        holiday("2027-01-01", "New Year")
        holiday("2027-01-14", "Pongal Holiday")
        holiday("2027-01-15", "Pongal")
        holiday("2027-01-16", "Uzhavar Thirunal")
        holiday("2027-01-23", "Thaipoosam")
        holiday("2027-01-26", "Republic Day")

        // March 2027
        holiday("2027-03-10", "Ramzan")
        holiday("2027-03-26", "Good Friday")

        // April 2027
        holiday("2027-04-08", "Ugadi")
        holiday("2027-04-14", "Tamil New Year")
        holiday("2027-04-19", "Mahavir Jayanti")

        // May 2027
        holiday("2027-05-01", "May Day")
        holiday("2027-05-17", "Bakrid")


        // ═══════════════════════════════════════════════════════════════
        // EXAMS — Midterm, End Semester Practical, End Semester
        // ═══════════════════════════════════════════════════════════════

        // --- Odd Semester (III, V, VII UG & III PG) ---
        exam("2026-08-07", "Commencement of Midterm Exam (Odd Sem)", "MIDTERM")
        exam("2026-09-24", "Commencement of Missed Midterm (Odd Sem)", "MIDTERM")
        exam("2026-10-05", "Commencement of End Semester Practical Exam (Odd Sem)", "END_SEMESTER")
        exam("2026-10-22", "Commencement of End Semester Exam (Odd Sem)", "END_SEMESTER")

        // --- Even Semester (IV, VI, VIII UG & IV PG) ---
        exam("2027-01-27", "Commencement of Midterm Exam for IV & VI Sem UG", "MIDTERM")
        exam("2027-03-08", "Commencement of Missed Midterm for IV & VI Sem UG", "MIDTERM")
        exam("2027-03-17", "Commencement of End Semester Practical Exam for IV & VI Sem UG", "END_SEMESTER")
        exam("2027-04-02", "Commencement of End Semester Exam for IV & VI Sem UG", "END_SEMESTER")


        // ═══════════════════════════════════════════════════════════════
        // ACADEMIC MILESTONES — Class commencement, last day, committees
        // ═══════════════════════════════════════════════════════════════

        // --- Odd Semester (III, V, VII UG & III PG) ---
        academic("2026-06-10", "Commencement of III, V, & VII Sem UG and III Sem PG Classes (CD01)")
        academic("2026-07-16", "First Class Committee Meeting (Odd Sem - CD26)")
        academic("2026-07-17", "First Class Committee Meeting (Odd Sem - CD27)")
        academic("2026-07-31", "Mid Semester Faculty Feedback (Odd Sem - CD37)")
        academic("2026-08-08", "Instructional Day - Friday Timetable (CD43)")
        academic("2026-09-22", "Second Class Committee Meeting (Odd Sem - CD72)")
        academic("2026-09-23", "Second Class Committee Meeting (Odd Sem - CD73)")
        academic("2026-10-10", "Instructional Day - Monday Timetable (CD85)")
        academic("2026-10-14", "End Semester Faculty Feedback (Odd Sem - CD88)")
        academic("2026-10-16", "Last Instruction Day (Odd Sem - CD90)")

        // --- Even Semester (IV, VI, VIII UG & IV PG) ---
        academic("2026-11-18", "Commencement of IV, VI & VIII Sem UG and IV Sem PG Classes (CD01)")
        academic("2026-12-23", "First Class Committee Meeting (Even Sem - CD26)")
        academic("2026-12-24", "First Class Committee Meeting (Even Sem - CD27)")
        academic("2027-01-08", "Mid Semester Faculty Feedback (Even Sem - CD36)")
        academic("2027-01-30", "Instructional Day - Friday Timetable (CD49)")
        academic("2027-03-04", "Second Class Committee Meeting (Even Sem - CD73)")
        academic("2027-03-05", "Second Class Committee Meeting (Even Sem - CD74)")
        academic("2027-03-29", "End Semester Faculty Feedback (Even Sem - CD88)")
        academic("2027-03-31", "Last Instruction Day (Even Sem - CD90)")

        // --- Next AY Commencement ---
        academic("2027-06-09", "Commencement of III, V & VII Sem UG and III Sem PG Classes (AY 2027-28)")

        return records
    }
}
