package acn.amrita.chen.planner.workspace

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.UUID

class DirectGeminiProvider(
    private val context: Context,
    private val repo: WorkspaceRepository
) : AiProvider {

    override suspend fun validate(key: String): List<String> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim()
        require(cleanKey.isNotBlank()) { "Enter an API key" }
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("x-goog-api-key", cleanKey)
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                val msg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrDefault(err)
                if (code == 400 || msg.contains("API_KEY_INVALID", ignoreCase = true) || msg.contains("API key not valid", ignoreCase = true)) {
                    throw IllegalStateException("Invalid Gemini API key. Please check your key in Google AI Studio.")
                }
                throw IllegalStateException("Gemini connection rejected ($code): $msg")
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(resp)
            val arr = j.optJSONArray("models") ?: JSONArray()
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val name = m.optString("name", "").removePrefix("models/")
                val methods = m.optJSONArray("supportedGenerationMethods")?.let { sm ->
                    (0 until sm.length()).map { sm.getString(it) }
                } ?: emptyList()
                if (methods.contains("generateContent") && name.startsWith("gemini-") &&
                    !name.contains("2.5") && !name.contains("image") && !name.contains("robotics") &&
                    !name.contains("tts") && !name.contains("live") && !name.contains("embedding") &&
                    !name.contains("audio") && !name.contains("transcribe") && !name.contains("computer-use")) {
                    list.add(name)
                }
            }
            if (list.isEmpty()) {
                listOf("gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite")
            } else {
                val priority = listOf("gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-flash-latest")
                list.sortedWith(
                    compareBy<String> { name ->
                        val idx = priority.indexOf(name)
                        if (idx >= 0) idx else 100
                    }.thenByDescending { it.contains("flash") }
                    .thenBy { it }
                )
            }
        } catch (e: Exception) {
            Log.e("Gemini", "validate failed", e)
            if (e is IllegalStateException) throw e
            throw IllegalStateException(safeError(e))
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun run(
        key: String,
        model: String,
        text: String,
        mode: String,
        group: String,
        messageIds: List<String>,
        attachments: List<String>
    ): JSONObject = withContext(Dispatchers.IO) {
        // If in a shared class group with an authenticated, verified user, try relay first
        if (group.isNotBlank() && repo.auth.currentUser?.isEmailVerified == true) {
            try {
                return@withContext GeminiRelayProvider(repo).run(key, model, text, mode, group, messageIds, attachments)
            } catch (e: Exception) {
                Log.w("Gemini", "Relay failed, falling back to direct API: ${e.message}")
            }
        }

        val cleanKey = key.trim()
        val requestedModel = if (model.isNotBlank() && !model.contains("2.5")) model else "gemini-3.6-flash"
        val user = repo.owner.value
        val allRecords = repo.dao.all(user)

        // Compile authenticated local context
        val courses = allRecords.filter { it.kind == "COURSE" && !it.deleted }
        val courseData = courses.map { c ->
            val pers = allRecords.find { it.id == "personal:${c.id}" }?.json()
            val attended = pers?.optInt("attended") ?: 0
            val total = pers?.optInt("total") ?: 0
            val pct = if (total > 0) String.format("%.1f", (attended.toDouble() / total) * 100.0) + "%" else "N/A"
            mapOf(
                "id" to c.id,
                "code" to c.json().optString("code"),
                "title" to c.json().optString("title"),
                "semester" to c.json().optInt("semester"),
                "attended" to attended,
                "total" to total,
                "percentage" to pct
            )
        }

        val timetable = allRecords.filter { it.kind == "TIMETABLE" && !it.deleted }.map {
            mapOf(
                "day" to it.json().optInt("day"),
                "startTime" to it.json().optString("startTime"),
                "endTime" to it.json().optString("endTime"),
                "room" to it.json().optString("room"),
                "courseId" to it.courseId
            )
        }

        val events = allRecords.filter { it.kind in listOf("MIDTERM", "END_SEMESTER", "QUIZ", "ASSIGNMENT", "PROJECT") && !it.deleted }.map {
            mapOf(
                "id" to it.id,
                "kind" to it.kind,
                "title" to it.json().optString("title"),
                "date" to it.json().optString("date"),
                "time" to it.json().optString("time"),
                "courseId" to it.courseId
            )
        }

        val chatHistory = allRecords.filter { it.kind == "CHAT" }
            .sortedBy { it.updatedAt }
            .takeLast(6)
            .filter { !it.title.trim().startsWith("{\"tool\"") }
            .map { mapOf("role" to it.json().optString("role"), "content" to it.title.take(1000)) }

        val today = LocalDate.now().toString()

        val systemPrompt = """
            You are the intelligent Academic Assistant for Amrita Vishwa Vidyapeetham students in ACN Planner.
            Today is $today (Asia/Kolkata timezone).
            
            AUTHENTIC LOCAL STUDENT DATA:
            - Registered Courses & Attendance: $courseData
            - Weekly Timetable: $timetable
            - Upcoming Events / Calendar: $events
            - Recent Chat History: $chatHistory
            
            INSTRUCTIONS:
            1. Never invent courses or attendance percentages. Always use the records listed above.
            2. When answering attendance questions:
               - Calculate exact percentages: (attended / total) * 100.
               - Classify status: Safe (>= 85%), Warning (75%–84%), Danger (< 75%).
               - If percentage > 75%, explain how many classes the student can afford to miss: floor((attended - 0.75 * total) / 0.75).
               - If percentage < 75%, calculate classes needed to reach 75%: ceil((0.75 * total - attended) / 0.25).
            3. If the user asks a question (Q&A mode, e.g., 'What is my attendance?', 'Am I safe to skip class?', 'What are my subjects?'):
               - Give a clear, polite, nicely formatted markdown response.
            4. If mode is 'extract' OR if the user explicitly asks to update/add/change the calendar, schedule, timetable, or exams (e.g., 'update my calendar', 'add exam on 25th'):
               - Formulate a JSON proposal with this exact schema:
               {
                 "answer": "Clear summary of the changes you are proposing for the student to review.",
                 "questions": [],
                 "changes": [
                   {
                     "id": "${UUID.randomUUID()}",
                     "kind": "MIDTERM" | "END_SEMESTER" | "QUIZ" | "ASSIGNMENT" | "PROJECT" | "TIMETABLE" | "STUDY_SESSION" | "NOTICE",
                     "courseId": "matching course ID from Registered Courses if applicable, or empty",
                     "action": "ADD" | "UPDATE" | "DELETE",
                     "expectedRevision": 0,
                     "data": {
                       "title": "Title of the item",
                       "content": "Details or notes",
                       "date": "YYYY-MM-DD",
                       "time": "HH:mm"
                     }
                   }
                 ]
               }
            5. Return clean JSON if proposing changes or pure text/JSON if answering. Do NOT wrap with extraneous explanations outside the JSON if returning JSON.
        """.trimIndent()

        val contentParts = JSONArray()
        for (att in attachments) {
            if (att.startsWith("content://") || att.startsWith("file://")) {
                try {
                    val u = Uri.parse(att)
                    val mime = context.contentResolver.getType(u) ?: "application/octet-stream"
                    val bytes: ByteArray? = context.contentResolver.openInputStream(u)?.use { stream -> stream.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        if (mime.startsWith("image/") || mime == "application/pdf") {
                            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            contentParts.put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", mime)
                                    put("data", b64)
                                })
                            })
                        } else {
                            val textContent = String(bytes, Charsets.UTF_8).take(15000)
                            contentParts.put(JSONObject().apply {
                                put("text", "Attached document content:\n$textContent")
                            })
                        }
                    }
                } catch (e: Exception) {
                    Log.w("Gemini", "Failed to process attachment $att: ${e.message}")
                }
            }
        }
        contentParts.put(JSONObject().put("text", text))

        val requestBody = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", contentParts)
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", 4096)
            })
        }

        val candidatesToTry = mutableListOf<String>()
        candidatesToTry.add(requestedModel)
        for (fb in listOf("gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-flash-latest")) {
            if (fb !in candidatesToTry) candidatesToTry.add(fb)
        }

        var lastError: Exception? = null
        var candidateText = ""

        for (attemptModel in candidatesToTry) {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$attemptModel:generateContent")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("x-goog-api-key", cleanKey)
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.connectTimeout = 30000
                conn.readTimeout = 30000
                conn.doOutput = true

                conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(requestBody.toString()) }

                val code = conn.responseCode
                if (code != 200) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    val errMsg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrDefault(err)
                    if (code == 400 && (errMsg.contains("API_KEY_INVALID", ignoreCase = true) || errMsg.contains("API key not valid", ignoreCase = true))) {
                        throw IllegalStateException("Invalid Gemini API key. Please check your key in Settings.")
                    }
                    if (code == 429) {
                        throw IllegalStateException("Gemini quota or rate limit exceeded. Please wait a moment and try again.")
                    }
                    if (code == 404 || code == 503) {
                        Log.w("Gemini", "Model $attemptModel failed ($code): $errMsg. Trying next fallback candidate...")
                        lastError = IllegalStateException("Gemini API error ($code): $errMsg")
                        continue
                    }
                    throw IllegalStateException("Gemini API error ($code): $errMsg")
                }

                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val respJson = JSONObject(resp)
                candidateText = respJson.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text", "") ?: ""
                lastError = null
                break
            } catch (e: Exception) {
                if (e is IllegalStateException && (e.message?.contains("Invalid Gemini API key") == true || e.message?.contains("quota") == true)) {
                    throw e
                }
                Log.w("Gemini", "Attempt with $attemptModel threw ${e.message}")
                lastError = e
            } finally {
                conn.disconnect()
            }
        }

        if (candidateText.isBlank() && lastError != null) {
            throw lastError
        }

        var clean = candidateText.trim()
        if (clean.startsWith("```json")) clean = clean.removePrefix("```json").trim()
        if (clean.startsWith("```")) clean = clean.removePrefix("```").trim()
        if (clean.endsWith("```")) clean = clean.removeSuffix("```").trim()

        var proposalObj: JSONObject? = null
        var answerText = clean

        if (clean.startsWith("{") && clean.endsWith("}")) {
            val parsed = runCatching { JSONObject(clean) }.getOrNull()
            if (parsed != null) {
                answerText = parsed.optString("answer", clean)
                val changes = parsed.optJSONArray("changes")
                if (changes != null && changes.length() > 0) {
                    proposalObj = JSONObject().apply {
                        put("id", UUID.randomUUID().toString())
                        put("owner", user)
                        put("status", "pending")
                        put("revision", 1)
                        put("answer", answerText)
                        put("changes", changes)
                        put("questions", parsed.optJSONArray("questions") ?: JSONArray())
                        put("sources", JSONArray())
                        put("createdAt", System.currentTimeMillis())
                    }
                }
            }
        }

        return@withContext JSONObject().apply {
            put("answer", answerText)
            if (proposalObj != null) put("proposal", proposalObj)
        }
    }
}
