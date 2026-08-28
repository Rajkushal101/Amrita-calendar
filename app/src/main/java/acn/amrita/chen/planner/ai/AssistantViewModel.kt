package acn.amrita.chen.planner.ai

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import acn.amrita.chen.planner.data.AcnRepository
import acn.amrita.chen.planner.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ChatMessage {
    data class User(val text: String, val attachmentCount: Int = 0) : ChatMessage()
    data class Ai(val text: String) : ChatMessage()
    data class ToolCall(val toolName: String, val description: String) : ChatMessage()
    object Thinking : ChatMessage()
}

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = AcnRepository(db)

    private val prefs = application.getSharedPreferences("acn_prefs", android.content.Context.MODE_PRIVATE)
    private val userRole = prefs.getString("user_role", "STUDENT") ?: "STUDENT"

    private val toolExecutor = AiToolExecutor(application, repository, userRole)

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(ChatMessage.Ai("Hi! I'm your ACN Planner AI Assistant. ✨\n\nI can help with your schedule, attendance, assignments, and exams. Try asking:\n• \"What's my next class?\"\n• \"Show my attendance\"\n• \"Any upcoming exams?\""))
    )
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isApiKeySet = MutableStateFlow(ApiKeyManager.hasApiKey(application))
    val isApiKeySet: StateFlow<Boolean> = _isApiKeySet

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun setApiKey(apiKey: String) {
        ApiKeyManager.saveApiKey(getApplication(), apiKey)
        _isApiKeySet.value = true
    }

    fun clearApiKey() {
        ApiKeyManager.clearApiKey(getApplication())
        _isApiKeySet.value = false
    }

    fun sendMessage(userMessage: String, uris: List<android.net.Uri> = emptyList()) {
        if (userMessage.isBlank() && uris.isEmpty()) return

        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(ChatMessage.User(userMessage, uris.size))
        _messages.value = currentMessages.toList()

        if (!_isApiKeySet.value) {
            currentMessages.add(ChatMessage.Ai("Please set your Gemini API key first to enable AI features. Tap the key icon above to enter your free API key from ai.google.dev"))
            _messages.value = currentMessages.toList()
            return
        }

        _isLoading.value = true
        currentMessages.add(ChatMessage.Thinking)
        _messages.value = currentMessages.toList()

        viewModelScope.launch {
            try {
                // Smart local tool routing — no API call needed for simple queries
                val localResponse = tryLocalToolResponse(userMessage)
                if (localResponse != null) {
                    currentMessages.remove(ChatMessage.Thinking)
                    currentMessages.add(ChatMessage.Ai(localResponse))
                    _messages.value = currentMessages.toList()
                    _isLoading.value = false
                    return@launch
                }

                // For complex queries, use the Gemini API with tool calling via system prompt
                val apiKey = ApiKeyManager.getApiKey(getApplication()) ?: ""
                Log.d("ACN_AI", "API Key present: ${apiKey.isNotBlank()}, length: ${apiKey.length}")
                
                val systemPrompt = """
                    You are the ACN Planner AI Assistant for Amrita Chennai students.
                    You have access to the following local tools:
                    - get_today_schedule
                    - get_next_class
                    - get_attendance (args: subjectCode)
                    - get_assignments (args: daysAhead)
                    - get_upcoming_exams
                    - get_announcements (args: limit)
                    - get_semester_progress
                    - get_attendance_what_if (args: subjectCode, attend: boolean)
                    - create_reminder (args: title, timeString)
                    - create_personal_event (args: title, dateString, type, timeString)
                    - cancel_class (args: sessionId, reason)
                    - post_announcement (args: title, body)
                    - generate_study_plan (no args)
                    - save_timetable (args: entries - JSON array string of objects with {day:Int(1=Mon), startTime:String(HH:mm), endTime:String(HH:mm), subjectCode:String, subjectName:String, room:String})
                    
                    If the user asks something that can be answered using one of these tools, respond with ONLY a JSON object containing the tool call. For example:
                    {"tool": "get_attendance", "args": {"subjectCode": "19CSE201"}}
                    
                    If no tool is needed, just respond naturally to the user. Be concise and helpful.
                """.trimIndent()
                
                val generativeModel = com.google.ai.client.generativeai.GenerativeModel(
                    modelName = "gemini-3.6-flash",
                    apiKey = apiKey,
                    systemInstruction = com.google.ai.client.generativeai.type.content { text(systemPrompt) }
                )

                // Execute Gemini call (off main thread)
                Log.d("ACN_AI", "Sending message to Gemini: $userMessage with ${uris.size} attachments")
                val responseText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val contentReq = com.google.ai.client.generativeai.type.content {
                        if (uris.isNotEmpty()) {
                            val cr = getApplication<Application>().contentResolver
                            for (uri in uris) {
                                try {
                                    val mimeType = cr.getType(uri) ?: "application/octet-stream"
                                    val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                                    if (bytes != null) {
                                        blob(mimeType, bytes)
                                    }
                                } catch (e: Exception) {
                                    Log.e("ACN_AI", "Failed to read attachment", e)
                                }
                            }
                        }
                        if (userMessage.isNotBlank()) {
                            text(userMessage)
                        }
                    }
                    val resp = generativeModel.generateContent(contentReq)
                    Log.d("ACN_AI", "Gemini response received: ${resp.text?.take(100)}")
                    resp.text ?: ""
                }

                // Parse if it's a tool call
                if (responseText.trim().startsWith("{") && responseText.trim().endsWith("}")) {
                    try {
                        val json = org.json.JSONObject(responseText.trim())
                        if (json.has("tool")) {
                            val tool = json.getString("tool")
                            val args = mutableMapOf<String, String>()
                            if (json.has("args")) {
                                val argsObj = json.getJSONObject("args")
                                argsObj.keys().forEach { k ->
                                    args[k] = argsObj.getString(k)
                                }
                            }
                            
                            // Show tool call indicator
                            currentMessages.remove(ChatMessage.Thinking)
                            currentMessages.add(ChatMessage.ToolCall(tool, "Executing internal tool..."))
                            currentMessages.add(ChatMessage.Thinking)
                            _messages.value = currentMessages.toList()

                            val toolResult = if (tool == "save_timetable") {
                                try {
                                    val argsObj = json.getJSONObject("args")
                                    val entriesStr = argsObj.getString("entries")
                                    // Sometimes Gemini might return the array directly as a JSONArray or as a stringified JSON array
                                    val entriesArray = if (entriesStr.startsWith("[")) {
                                        org.json.JSONArray(entriesStr)
                                    } else {
                                        argsObj.getJSONArray("entries")
                                    }
                                    
                                    val timetableEntries = mutableListOf<acn.amrita.chen.planner.data.TimetableEntry>()
                                    for (i in 0 until entriesArray.length()) {
                                        val entry = entriesArray.getJSONObject(i)
                                        timetableEntries.add(
                                            acn.amrita.chen.planner.data.TimetableEntry(
                                                day = entry.getInt("day"),
                                                startTime = entry.getString("startTime"),
                                                endTime = entry.getString("endTime"),
                                                subjectCode = entry.getString("subjectCode"),
                                                subjectName = entry.getString("subjectName"),
                                                room = entry.getString("room")
                                            )
                                        )
                                    }
                                    repository.saveTimetable(timetableEntries)
                                    "Timetable saved successfully. User can now view it in the Timetable tab."
                                } catch (e: Exception) {
                                    "Failed to save timetable: ${e.message}"
                                }
                            } else {
                                toolExecutor.execute(tool, args)
                            }
                            
                            // Send tool result back to Gemini for natural language formulation
                            val finalPrompt = "User query: $userMessage\n\nTool Result: $toolResult\n\nExplain this to the user naturally and concisely."
                            val finalResponseText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val finalResp = generativeModel.generateContent(finalPrompt)
                                finalResp.text ?: ""
                            }
                            currentMessages.remove(ChatMessage.Thinking)
                            // Optionally remove the tool call indicator, but let's keep it for visual debug
                            currentMessages.add(ChatMessage.Ai(finalResponseText))
                        } else {
                            currentMessages.remove(ChatMessage.Thinking)
                            currentMessages.add(ChatMessage.Ai(responseText))
                        }
                    } catch (e: Exception) {
                        currentMessages.remove(ChatMessage.Thinking)
                        currentMessages.add(ChatMessage.Ai(responseText))
                    }
                } else {
                    currentMessages.remove(ChatMessage.Thinking)
                    currentMessages.add(ChatMessage.Ai(responseText))
                }
                
                _messages.value = currentMessages.toList()
            } catch (e: Exception) {
                Log.e("ACN_AI", "Gemini API error", e)
                currentMessages.remove(ChatMessage.Thinking)
                val errorMsg = when {
                    e.message?.contains("API key", ignoreCase = true) == true ->
                        "❌ Invalid API key. Please update your Gemini API key by tapping the key icon above.\n\nGet a free key at: aistudio.google.com"
                    e.message?.contains("network", ignoreCase = true) == true || 
                    e.message?.contains("connect", ignoreCase = true) == true ->
                        "❌ Network error. Please check your internet connection and try again."
                    e.message?.contains("quota", ignoreCase = true) == true || 
                    e.message?.contains("rate", ignoreCase = true) == true ->
                        "❌ API rate limit reached. Please wait a moment and try again."
                    e.message?.contains("not found", ignoreCase = true) == true || 
                    e.message?.contains("404", ignoreCase = true) == true ->
                        "❌ Model not available. The Gemini model may have been updated. Error: ${e.message}"
                    else ->
                        "❌ Error: ${e.message}\n\nPlease check your API key and internet connection."
                }
                currentMessages.add(ChatMessage.Ai(errorMsg))
                _messages.value = currentMessages.toList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun simulatePdfImport() {
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(ChatMessage.User("Simulate Academic Calendar PDF Import"))
        
        // Simulating the backend process that reads the PDF and extracts events
        val simulatedResponse = """
            **PDF Processing Complete** 📄✅
            
            I have extracted the following key dates from the uploaded Academic Calendar (2026-2027) and synced them to your database:
            • **Aug 20:** Semester Start (Fall 2026)
            • **Oct 5:** Mid-Term Exams Begin
            • **Nov 26:** Thanksgiving Break
            • **Dec 10:** Last Day of Instruction
            • **Dec 12:** Final Exams Begin
            
            These have been added as Events and will now appear in your timeline!
        """.trimIndent()
        
        currentMessages.add(ChatMessage.Ai(simulatedResponse))
        _messages.value = currentMessages.toList()
    }

    /**
     * Smart local routing — handles common queries using tool executor directly
     * without needing the Gemini API round-trip.
     */
    private suspend fun tryLocalToolResponse(query: String): String? {
        val q = query.lowercase()
        return when {
            q.contains("next class") || q.contains("upcoming class") -> {
                val result = toolExecutor.execute("get_next_class", emptyMap())
                formatToolResult("Next Class", result)
            }
            q.contains("today") && (q.contains("schedule") || q.contains("class")) -> {
                val result = toolExecutor.execute("get_today_schedule", emptyMap())
                formatToolResult("Today's Schedule", result)
            }
            q.contains("attendance") && !q.contains("what if") && !q.contains("skip") -> {
                val result = toolExecutor.execute("get_attendance", emptyMap())
                formatToolResult("Attendance", result)
            }
            q.contains("assignment") || q.contains("homework") || q.contains("due") -> {
                val result = toolExecutor.execute("get_assignments", emptyMap())
                formatToolResult("Assignments", result)
            }
            q.contains("exam") -> {
                val result = toolExecutor.execute("get_upcoming_exams", emptyMap())
                formatToolResult("Upcoming Exams", result)
            }
            q.contains("announcement") || q.contains("notice") -> {
                val result = toolExecutor.execute("get_announcements", emptyMap())
                formatToolResult("Announcements", result)
            }
            q.contains("semester") && q.contains("progress") -> {
                val result = toolExecutor.execute("get_semester_progress", emptyMap())
                formatToolResult("Semester Progress", result)
            }
            else -> null // Fall through to Gemini API
        }
    }

    private fun formatToolResult(title: String, jsonResult: String): String {
        return try {
            val json = org.json.JSONObject(jsonResult)
            if (json.has("error")) {
                "❌ ${json.getString("error")}"
            } else if (json.has("result")) {
                "📋 **$title**\n${json.getString("result")}"
            } else {
                "📋 **$title**\n${prettifyJson(json)}"
            }
        } catch (e: Exception) {
            jsonResult
        }
    }

    private fun prettifyJson(json: org.json.JSONObject): String {
        val sb = StringBuilder()
        json.keys().forEach { key ->
            when (val value = json.get(key)) {
                is org.json.JSONArray -> {
                    for (i in 0 until value.length()) {
                        val item = value.getJSONObject(i)
                        sb.appendLine("• ${item.optString("title", item.optString("subject", item.optString("subjectId", "Item ${i+1}")))}")
                        item.keys().forEach { k ->
                            if (k != "title" && k != "subject" && k != "subjectId") {
                                sb.appendLine("  $k: ${item.get(k)}")
                            }
                        }
                    }
                }
                else -> sb.appendLine("$key: $value")
            }
        }
        return sb.toString().trimEnd()
    }
}
