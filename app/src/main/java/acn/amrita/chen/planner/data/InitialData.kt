package acn.amrita.chen.planner.data

import java.time.LocalDate
import java.time.ZoneId

object InitialData {
    private fun getMillis(year: Int, month: Int, day: Int): Long {
        return LocalDate.of(year, month, day)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    fun getAcademicEventsAndHolidays(): List<Event> {
        return listOf(
            Event(title = "Holiday", dateMillis = getMillis(2026, 6, 6), type = "holiday"),
            Event(title = "CD01 — Commencement of III,V,VII Sem UG & III Sem PG Classes", dateMillis = getMillis(2026, 6, 10), type = "academic"),
            Event(title = "Non-Instructional Day (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 6, 13), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 6, 14), type = "holiday"),
            Event(title = "Non-Instructional Day (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 6, 20), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 6, 21), type = "holiday"),
            Event(title = "Holiday — Muharram", dateMillis = getMillis(2026, 6, 26), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 6, 27), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 6, 28), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 7, 4), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 7, 5), type = "holiday"),
            Event(title = "Non-Instructional Day (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 7, 11), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 7, 12), type = "holiday"),
            Event(title = "First Class Committee Meeting (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 7, 16), type = "meeting"),
            Event(title = "First Class Committee Meeting (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 7, 17), type = "meeting"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 7, 18), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 7, 19), type = "holiday"),
            Event(title = "Non-Instructional Day (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 7, 25), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 7, 26), type = "holiday"),
            Event(title = "Mid Semester Faculty Feedback (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 7, 31), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 8, 1), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 8, 2), type = "holiday"),
            Event(title = "Commencement of Midterm Exam (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 8, 7), type = "exam"),
            Event(title = "CD43 — Instructional Day (Friday TT)", dateMillis = getMillis(2026, 8, 8), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 8, 9), type = "holiday"),
            Event(title = "Independence Day 🇮🇳", dateMillis = getMillis(2026, 8, 15), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 8, 16), type = "holiday"),
            Event(title = "Non-Instructional Day (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 8, 22), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 8, 23), type = "holiday"),
            Event(title = "Holiday — Onam Sadhya / Milad-un-Nabi", dateMillis = getMillis(2026, 8, 26), type = "holiday"),
            Event(title = "Non-Instructional Day (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 8, 29), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 8, 30), type = "holiday"),
            Event(title = "Holiday — Krishna Janmashtami", dateMillis = getMillis(2026, 9, 4), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 9, 5), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 9, 6), type = "holiday"),
            Event(title = "Non-Instructional Day (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 9, 12), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 9, 13), type = "holiday"),
            Event(title = "Holiday — Vinayakar Chaturthi", dateMillis = getMillis(2026, 9, 14), type = "holiday"),
            Event(title = "Non-Instructional Day (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 9, 19), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 9, 20), type = "holiday"),
            Event(title = "Second Class Committee Meeting (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 9, 22), type = "meeting"),
            Event(title = "Second Class Committee Meeting (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 9, 23), type = "meeting"),
            Event(title = "Commencement of Missed Midterm (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 9, 24), type = "exam"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 9, 26), type = "holiday"),
            Event(title = "Holiday — Amma's Jayanthi", dateMillis = getMillis(2026, 9, 27), type = "holiday"),
            Event(title = "Holiday — Gandhi Jayanthi", dateMillis = getMillis(2026, 10, 2), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 10, 3), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 10, 4), type = "holiday"),
            Event(title = "Commencement of End Semester Practical Exam (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 10, 5), type = "exam"),
            Event(title = "CD85 — Instructional Day (Monday TT)", dateMillis = getMillis(2026, 10, 10), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 10, 11), type = "holiday"),
            Event(title = "End Semester Faculty Feedback (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 10, 14), type = "academic"),
            Event(title = "📚 Last Instruction Day (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 10, 16), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 10, 18), type = "holiday"),
            Event(title = "Holiday — Ayutha Pooja", dateMillis = getMillis(2026, 10, 19), type = "holiday"),
            Event(title = "Holiday — Vijaya Dasami", dateMillis = getMillis(2026, 10, 20), type = "holiday"),
            Event(title = "Commencement of End Semester Exam (III,V,VII UG & III PG)", dateMillis = getMillis(2026, 10, 22), type = "exam"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 10, 25), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 11, 1), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 11, 7), type = "holiday"),
            Event(title = "Holiday — Deepavali 🪔", dateMillis = getMillis(2026, 11, 8), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 11, 9), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 11, 15), type = "holiday"),
            Event(title = "CD01 — Commencement of IV,VI,VIII Sem UG & IV Sem PG Classes", dateMillis = getMillis(2026, 11, 18), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 11, 21), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 11, 22), type = "holiday"),
            Event(title = "Non-Instructional Day (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2026, 11, 28), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 11, 29), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 12, 5), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 12, 6), type = "holiday"),
            Event(title = "Non-Instructional Day (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2026, 12, 12), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 12, 13), type = "holiday"),
            Event(title = "Non-Instructional Day (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2026, 12, 19), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 12, 20), type = "holiday"),
            Event(title = "First Class Committee Meeting (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2026, 12, 23), type = "meeting"),
            Event(title = "First Class Committee Meeting (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2026, 12, 24), type = "meeting"),
            Event(title = "Holiday — Christmas 🎄", dateMillis = getMillis(2026, 12, 25), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 12, 26), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2026, 12, 27), type = "holiday"),
            Event(title = "New Year's Day 🎆", dateMillis = getMillis(2027, 1, 1), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 1, 2), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 1, 3), type = "holiday"),
            Event(title = "Mid Semester Faculty Feedback (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2027, 1, 8), type = "academic"),
            Event(title = "Non-Instructional Day (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2027, 1, 9), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 1, 10), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 1, 14), type = "holiday"),
            Event(title = "Holiday — Pongal 🌾", dateMillis = getMillis(2027, 1, 15), type = "holiday"),
            Event(title = "Holiday — Uzhavar Thirunal", dateMillis = getMillis(2027, 1, 16), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 1, 17), type = "holiday"),
            Event(title = "Holiday — Thaipoosam", dateMillis = getMillis(2027, 1, 23), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 1, 24), type = "holiday"),
            Event(title = "Holiday — Republic Day 🇮🇳", dateMillis = getMillis(2027, 1, 26), type = "holiday"),
            Event(title = "Commencement of Midterm Exam (IV,VI UG)", dateMillis = getMillis(2027, 1, 27), type = "exam"),
            Event(title = "CD49 — Instructional Day (Friday TT)", dateMillis = getMillis(2027, 1, 30), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 1, 31), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 2, 6), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 2, 7), type = "holiday"),
            Event(title = "Non-Instructional Day (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2027, 2, 13), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 2, 14), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 2, 20), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 2, 21), type = "holiday"),
            Event(title = "Non-Instructional Day (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2027, 2, 27), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 2, 28), type = "holiday"),
            Event(title = "Second Class Committee Meeting (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2027, 3, 4), type = "meeting"),
            Event(title = "Second Class Committee Meeting (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2027, 3, 5), type = "meeting"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 3, 6), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 3, 7), type = "holiday"),
            Event(title = "Commencement of Missed Midterm (IV,VI UG)", dateMillis = getMillis(2027, 3, 8), type = "exam"),
            Event(title = "Holiday — Ramzan 🌙", dateMillis = getMillis(2027, 3, 10), type = "holiday"),
            Event(title = "Non-Instructional Day (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2027, 3, 13), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 3, 14), type = "holiday"),
            Event(title = "Commencement of End Semester Practical Exam (IV,VI UG)", dateMillis = getMillis(2027, 3, 17), type = "exam"),
            Event(title = "Non-Instructional Day (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2027, 3, 20), type = "academic"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 3, 21), type = "holiday"),
            Event(title = "Holiday — Good Friday ✝️", dateMillis = getMillis(2027, 3, 26), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 3, 27), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 3, 28), type = "holiday"),
            Event(title = "End Semester Faculty Feedback (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2027, 3, 29), type = "academic"),
            Event(title = "📚 Last Instruction Day (IV,VI,VIII UG & IV PG)", dateMillis = getMillis(2027, 3, 31), type = "academic"),
            Event(title = "Commencement of End Semester Exam (IV,VI UG)", dateMillis = getMillis(2027, 4, 2), type = "exam"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 4, 3), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 4, 4), type = "holiday"),
            Event(title = "Holiday — Ugadi", dateMillis = getMillis(2027, 4, 8), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 4, 11), type = "holiday"),
            Event(title = "Holiday — Tamil New Year 🎊", dateMillis = getMillis(2027, 4, 14), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 4, 18), type = "holiday"),
            Event(title = "Holiday — Mahavir Jayanti", dateMillis = getMillis(2027, 4, 19), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 4, 24), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 4, 25), type = "holiday"),
            Event(title = "Holiday — May Day / Labour Day", dateMillis = getMillis(2027, 5, 1), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 5, 2), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 5, 9), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 5, 16), type = "holiday"),
            Event(title = "Holiday — Bakrid 🌙", dateMillis = getMillis(2027, 5, 17), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 5, 23), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 5, 29), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 5, 30), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 6, 5), type = "holiday"),
            Event(title = "Holiday", dateMillis = getMillis(2027, 6, 6), type = "holiday"),
            Event(title = "CD01 — Commencement of III,V,VII Sem UG & III Sem PG Classes (Next AY)", dateMillis = getMillis(2027, 6, 9), type = "academic"),
        )
    }

    fun getDummySessions(): List<ClassSession> {
        val sessions = mutableListOf<ClassSession>()
        // Generate a standard dummy week
        for (day in 1..5) { // Mon to Fri
            // 9:00 AM to 10:40 AM (100 mins)
            sessions.add(ClassSession(subjectId = 101, facultyId = "FAC01", room = "A-201", dayOfWeek = day, startTimeMinutes = 9 * 60, endTimeMinutes = 10 * 60 + 40, section = "A", semester = 3, batch = "B1"))
            // 11:00 AM to 12:40 PM (100 mins)
            sessions.add(ClassSession(subjectId = 102, facultyId = "FAC02", room = "A-202", dayOfWeek = day, startTimeMinutes = 11 * 60, endTimeMinutes = 12 * 60 + 40, section = "A", semester = 3, batch = "B1"))
            // 1:30 PM to 3:10 PM (100 mins)
            sessions.add(ClassSession(subjectId = 103, facultyId = "FAC03", room = "Lab 1", dayOfWeek = day, startTimeMinutes = 13 * 60 + 30, endTimeMinutes = 15 * 60 + 10, section = "A", semester = 3, batch = "B1"))
        }
        // Make Monday 9AM cancelled to show status
        val firstSession = sessions.first()
        sessions[0] = firstSession.copy(status = SessionStatus.CANCELLED, cancelledBy = "FAC01")
        return sessions
    }
}
