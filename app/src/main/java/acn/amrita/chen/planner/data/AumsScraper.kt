package acn.amrita.chen.planner.data

import org.jsoup.Jsoup

object AumsScraper {
    data class ParsedAttendance(
        val subjectCode: String,
        val subjectName: String,
        val totalClasses: Int,
        val attendedClasses: Int
    )

    data class ParsedAumsData(
        val userName: String?,
        val attendanceList: List<ParsedAttendance>,
        val warnings: List<String> = emptyList(),
        val academicTerm: String? = null,
        val detectedSemester: Int? = null
    )

    private val courseCodeRegex = Regex("""\b([0-9]{2}[A-Za-z]{2,5}[0-9]{3}[A-Za-z]?|[A-Za-z]{2,5}[0-9]{3}[A-Za-z]?)\b""")
    private val termRegex = Regex("""\b(20\d{2}\s*-\s*\d{2,4}\s+(?:Odd|Even|Term\s*\d+)(?:\s*Sem(?:ester)?)?)\b""", RegexOption.IGNORE_CASE)
    private val semesterRegex = Regex("""\b(?:Semester\s*([1-9]|1[0-2])|([1-9]|1[0-2])(?:st|nd|rd|th)\s*Sem(?:ester)?)\b""", RegexOption.IGNORE_CASE)

    fun parseAttendanceHtml(html: String): ParsedAumsData {
        val result = mutableListOf<ParsedAttendance>()
        val warnings = mutableListOf<String>()
        val doc = Jsoup.parse(html)

        fun normalized(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

        // 1. Extract Academic Term from page (dropdowns, selected options, or body text)
        var academicTerm: String? = null
        for (el in doc.select("select option[selected], .select2-selection__rendered, [class*='term'], [class*='academic'], [id*='term'], [class*='dropdown']")) {
            val t = el.text().trim()
            val m = termRegex.find(t)
            if (m != null) {
                academicTerm = m.value.trim()
                break
            }
        }
        if (academicTerm == null) {
            academicTerm = termRegex.find(doc.text())?.value?.trim()
        }

        var detectedSemester: Int? = null
        val directSemMatch = semesterRegex.find(doc.text())
        if (directSemMatch != null) {
            detectedSemester = (directSemMatch.groups[1] ?: directSemMatch.groups[2])?.value?.toIntOrNull()
        }

        var detectedBatchYear: Int? = null

        // 2. Search tables for attendance rows
        for (table in doc.select("table")) {
            val rows = table.select("tr")
            if (rows.isEmpty()) continue

            // Find header row containing course column and attendance column
            val header = rows.firstOrNull { row ->
                val texts = row.select("th,td").map { normalized(it.text()) }
                val hasCourse = texts.any {
                    it in setOf("course", "coursecode", "subjectcode", "coursename", "subjectname", "subject", "classname", "class", "subjecttitle", "coursetitle")
                }
                val hasAttendance = texts.any {
                    it in setOf(
                        "totalclasses", "classesconducted", "totalhours", "conducted", "total", "totalconducted",
                        "conductedclasses", "totaldelivered", "delivered", "classesheld", "hoursconducted",
                        "attendedclasses", "classesattended", "attendedhours", "attended", "present", "hoursattended",
                        "presentclasses", "totalattended", "percentage", "percent", "attendancepercentage", "pct"
                    ) || it.contains("attend") || it.contains("present") || it.contains("conduct") || (it.contains("total") && !it.contains("mark"))
                }
                hasCourse && hasAttendance
            } ?: continue

            val columns = header.select("th,td").map { normalized(it.text()) }

            val separateCode = columns.indexOfFirst { it in setOf("coursecode", "subjectcode") }
            val separateName = columns.indexOfFirst { it in setOf("coursename", "subjectname", "coursetitle", "subjecttitle") }
            val courseCol = if (separateCode >= 0) separateCode else columns.indexOfFirst { it in setOf("course", "subject", "coursecode", "subjectcode") }
            val classNameCol = columns.indexOfFirst { it in setOf("classname", "class") }

            val attendedCol = columns.indexOfFirst {
                it in setOf("attendedclasses", "classesattended", "attendedhours", "hoursattended", "totalattended", "attended", "present", "presentclasses")
            }.takeIf { it >= 0 } ?: columns.indexOfFirst { it.contains("attend") || it.contains("present") }

            val dutyLeaveCol = columns.indexOfFirst {
                it in setOf("dutyleave", "od", "onduty", "duty")
            }

            val totalCol = columns.indexOfFirst {
                it in setOf("totalclasses", "classesconducted", "totalhours", "hoursconducted", "totalconducted", "conductedclasses", "conducted", "total", "totaldelivered", "delivered", "classesheld")
            }.takeIf { it >= 0 } ?: columns.indexOfFirst { (it.contains("conduct") || it.contains("total") || it.contains("held")) && !it.contains("attend") && !it.contains("present") && !it.contains("mark") }

            val combinedCol = if (totalCol < 0 || attendedCol < 0) {
                columns.indexOfFirst { it.contains("attend") || it.contains("present") || it.contains("conduct") }
            } else -1

            if (courseCol < 0 && classNameCol < 0) {
                warnings += "Unrecognized course column; table skipped"
                continue
            }
            if ((totalCol < 0 || attendedCol < 0) && combinedCol < 0) {
                warnings += "Unrecognized attendance columns; table skipped"
                continue
            }

            for (row in rows.drop(rows.indexOf(header) + 1)) {
                val cells = row.select("th, td")
                if (cells.isEmpty()) continue

                val cellTexts = cells.map { normalized(it.text()) }
                if (cellTexts == columns) continue // Skip duplicate header rows


                val classText = if (classNameCol >= 0) cells.getOrNull(classNameCol)?.text()?.trim().orEmpty() else ""
                val courseText = if (courseCol >= 0) cells.getOrNull(courseCol)?.text()?.trim().orEmpty() else ""

                // Extract batch year if present in class name (e.g. B.Tech..2023.R.CYS.1.20CYS402)
                if (detectedBatchYear == null && classText.isNotBlank()) {
                    Regex("""\b(20\d{2})\b""").find(classText)?.let {
                        detectedBatchYear = it.value.toIntOrNull()
                    }
                }

                var code = ""
                var name = ""

                if (separateCode >= 0 && separateName >= 0) {
                    code = cells.getOrNull(separateCode)?.text()?.trim().orEmpty()
                    name = cells.getOrNull(separateName)?.text()?.trim().orEmpty().ifBlank { code }
                } else {
                    val codeMatchCourse = courseCodeRegex.find(courseText)
                    val codeMatchClass = courseCodeRegex.find(classText)

                    code = codeMatchCourse?.value
                        ?: codeMatchClass?.value
                        ?: classText.split(Regex("""[._\s]+""")).lastOrNull { it.matches(Regex("""[A-Za-z0-9]{4,15}""")) }
                        ?: courseText.split(Regex("""\s+""")).firstOrNull { it.matches(Regex("""[A-Za-z0-9]{4,15}""")) }
                        ?: ""

                    if (code.isNotBlank()) {
                        val cleanedTitle = courseText.replace(code, "").trim().trim('-', ':', '·', '/', ' ')
                        name = if (cleanedTitle.isNotBlank()) cleanedTitle else code
                    } else if (courseText.isNotBlank()) {
                        name = courseText
                        code = courseText
                    }
                }

                fun parseCount(s: String?): Int? {
                    if (s.isNullOrBlank()) return null
                    val clean = s.trim().replace(",", "")
                    return clean.toIntOrNull()
                        ?: clean.toDoubleOrNull()?.toInt()
                        ?: Regex("""\b\d+\b""").find(clean)?.value?.toIntOrNull()
                }

                var t: Int? = if (totalCol >= 0) parseCount(cells.getOrNull(totalCol)?.text()) else null
                var a: Int? = if (attendedCol >= 0) parseCount(cells.getOrNull(attendedCol)?.text()) else null

                if (dutyLeaveCol >= 0 && a != null) {
                    val dl = parseCount(cells.getOrNull(dutyLeaveCol)?.text()) ?: 0
                    if (dl > 0) {
                        a = if (t != null) (a + dl).coerceAtMost(t) else (a + dl)
                    }
                }

                if ((t == null || a == null) && combinedCol >= 0) {
                    val combinedText = cells.getOrNull(combinedCol)?.text().orEmpty()
                    val match = Regex("""(\d+)\s*[/of]\s*(\d+)""").find(combinedText)
                    if (match != null) {
                        a = match.groupValues[1].toIntOrNull()
                        t = match.groupValues[2].toIntOrNull()
                    }
                }

                val sanitizedCode = code.uppercase().trim()
                if (!sanitizedCode.matches(Regex("[A-Za-z0-9._-]{3,40}")) || t == null || a == null || t < 0 || a !in 0..t) {
                    warnings += "A row has invalid attendance counts; skipped"
                    continue
                }

                val existing = result.find { it.subjectCode == sanitizedCode }
                val parsed = ParsedAttendance(sanitizedCode, name.ifBlank { sanitizedCode }, t, a)
                if (existing != null && existing != parsed) {
                    warnings += "Conflicting rows for $sanitizedCode"
                    return ParsedAumsData(null, emptyList(), warnings, academicTerm, detectedSemester)
                }
                if (existing == null) {
                    result += parsed
                }
            }
        }

        // 3. Deduce semester from academic term & batch year if not directly detected
        if (detectedSemester == null && academicTerm != null && detectedBatchYear != null) {
            val termYearMatch = Regex("""\b(20\d{2})\b""").find(academicTerm)
            val termStartYear = termYearMatch?.value?.toIntOrNull()
            if (termStartYear != null && termStartYear >= detectedBatchYear!!) {
                val isOdd = academicTerm.contains("Odd", ignoreCase = true)
                val isEven = academicTerm.contains("Even", ignoreCase = true)
                val sem = (termStartYear - detectedBatchYear!!) * 2 + (if (isOdd) 1 else if (isEven) 2 else 1)
                if (sem in 1..12) {
                    detectedSemester = sem
                }
            }
        }

        if (result.isEmpty()) {
            warnings += "No recognized attendance table. Open the attendance page and try again."
        }

        return ParsedAumsData(null, result, warnings.distinct(), academicTerm, detectedSemester)
    }
}

