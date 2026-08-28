package acn.amrita.chen.planner.data

import org.jsoup.Jsoup

object AumsScraper {
    
    data class ParsedAttendance(
        val subjectCode: String,
        val subjectName: String,
        val totalClasses: Int,
        val attendedClasses: Int
    )

    fun parseAttendanceHtml(html: String): List<ParsedAttendance> {
        val parsedList = mutableListOf<ParsedAttendance>()
        try {
            val doc = Jsoup.parse(html)
            
            // Search all tables
            val tables = doc.select("table")
            for (table in tables) {
                // Check if this is the attendance table by looking at headers or text
                val text = table.text()
                if (text.contains("Course Code", ignoreCase = true) || 
                    text.contains("Total Classes", ignoreCase = true) ||
                    text.contains("Attended", ignoreCase = true)) {
                    
                    val rows = table.select("tr")
                    for (row in rows) {
                        val cols = row.select("td").map { it.text().trim() }
                        // Typical Amrita AUMS row has 7-9 columns.
                        if (cols.size >= 6) { 
                            // Try to extract course code (usually matches pattern like 21CYS301)
                            val codePattern = Regex("^[0-9]{2}[A-Z]{3}[0-9]{3,4}$")
                            val code = cols.firstOrNull { it.matches(codePattern) } 
                                       ?: cols.getOrNull(1)?.takeIf { it.isNotBlank() }
                                       ?: continue
                                       
                            val name = cols.getOrNull(2) ?: ""
                            
                            // Extract numeric columns to find Total and Attended
                            val numericCols = cols.mapNotNull { it.toIntOrNull() }
                            if (numericCols.size >= 2) {
                                // In AUMS, Total Classes comes before Attended Classes usually
                                val total = numericCols[0]
                                val attended = numericCols[1]
                                
                                parsedList.add(
                                    ParsedAttendance(
                                        subjectCode = code,
                                        subjectName = name,
                                        totalClasses = Math.max(total, attended), // Safety check
                                        attendedClasses = Math.min(total, attended)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Remove duplicates if any
        return parsedList.distinctBy { it.subjectCode }
    }
}
