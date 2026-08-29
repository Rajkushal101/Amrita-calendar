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
import acn.amrita.chen.planner.data.AppPreferences

import kotlinx.coroutines.flow.take

sealed class ChatMessage {
    data class User(val text: String, val attachmentCount: Int = 0) : ChatMessage()
    data class Ai(val text: String) : ChatMessage()
    data class ToolCall(val toolName: String, val description: String) : ChatMessage()
    object Thinking : ChatMessage()
    data class EventCard(val title: String, val date: String, val type: String) : ChatMessage()
    data class TaskCard(val title: String, val due: String, val priority: String) : ChatMessage()
}

fun ChatMessage.toEntity(): acn.amrita.chen.planner.data.ChatMessageEntity? {
    return when (this) {
        is ChatMessage.User -> acn.amrita.chen.planner.data.ChatMessageEntity(type = "USER", content = this.text, extra1 = this.attachmentCount.toString())
        is ChatMessage.Ai -> acn.amrita.chen.planner.data.ChatMessageEntity(type = "AI", content = this.text)
        is ChatMessage.ToolCall -> acn.amrita.chen.planner.data.ChatMessageEntity(type = "TOOL_CALL", content = this.toolName, extra1 = this.description)
        is ChatMessage.EventCard -> acn.amrita.chen.planner.data.ChatMessageEntity(type = "EVENT_CARD", content = this.title, extra1 = this.date, extra2 = this.type)
        is ChatMessage.TaskCard -> acn.amrita.chen.planner.data.ChatMessageEntity(type = "TASK_CARD", content = this.title, extra1 = this.due, extra2 = this.priority)
        ChatMessage.Thinking -> null // Don't persist thinking state
    }
}

fun acn.amrita.chen.planner.data.ChatMessageEntity.toDomain(): ChatMessage {
    return when (type) {
        "USER" -> ChatMessage.User(content, extra1.toIntOrNull() ?: 0)
        "AI" -> ChatMessage.Ai(content)
        "TOOL_CALL" -> ChatMessage.ToolCall(content, extra1)
        "EVENT_CARD" -> ChatMessage.EventCard(content, extra1, extra2)
        "TASK_CARD" -> ChatMessage.TaskCard(content, extra1, extra2)
        else -> ChatMessage.Ai(content)
    }
}

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = AcnRepository(db)

    private val prefs = application.getSharedPreferences("acn_prefs", android.content.Context.MODE_PRIVATE)
    private val userRole = prefs.getString("user_role", "STUDENT") ?: "STUDENT"
    private val appPreferences = AppPreferences(application)

    private val toolExecutor = AiToolExecutor(application, repository, userRole)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isApiKeySet = MutableStateFlow(ApiKeyManager.hasApiKey(application))
    val isApiKeySet: StateFlow<Boolean> = _isApiKeySet

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _navigationEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val navigationEvents: kotlinx.coroutines.flow.SharedFlow<String> = _navigationEvents
    
    var currentRoute: String = "home"
    
    init {
        viewModelScope.launch {
            db.chatMessageDao().getAllMessages().take(1).collect { entities ->
                if (entities.isEmpty()) {
                    val initial = ChatMessage.Ai("Hi! I'm your ACN Planner AI Assistant. ✨\n\nI can help with your schedule, attendance, assignments, and exams. Try asking:\n• \"What's my next class?\"\n• \"Show my attendance\"\n• \"Any upcoming exams?\"")
                    _messages.value = listOf(initial)
                    initial.toEntity()?.let { db.chatMessageDao().insertMessage(it) }
                } else {
                    _messages.value = entities.map { it.toDomain() }
                }
            }
        }
    }

    private fun addMessage(msg: ChatMessage) {
        val current = _messages.value.toMutableList()
        current.add(msg)
        _messages.value = current
        viewModelScope.launch {
            msg.toEntity()?.let { db.chatMessageDao().insertMessage(it) }
        }
    }

    fun setApiKey(apiKey: String) {
        ApiKeyManager.saveApiKey(getApplication(), apiKey)
        _isApiKeySet.value = true
    }

    fun clearApiKey() {
        ApiKeyManager.clearApiKey(getApplication())
        _isApiKeySet.value = false
    }

    fun sendMessage(userMessage: String, uris: List<android.net.Uri> = emptyList()) {
        val currentMessages = _messages.value.toMutableList()
        if (userMessage.isNotBlank() || uris.isNotEmpty()) {
            val userMsg = ChatMessage.User(userMessage, uris.size)
            currentMessages.add(userMsg)
            _messages.value = currentMessages.toList()
            viewModelScope.launch { userMsg.toEntity()?.let { db.chatMessageDao().insertMessage(it) } }
        }

        if (!_isApiKeySet.value) {
            val errorMsg = ChatMessage.Ai("Please set your Gemini API key first to enable AI features. Tap the key icon above to enter your free API key from ai.google.dev")
            currentMessages.add(errorMsg)
            _messages.value = currentMessages.toList()
            viewModelScope.launch { errorMsg.toEntity()?.let { db.chatMessageDao().insertMessage(it) } }
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
                    val aiMsg = ChatMessage.Ai(localResponse)
                    currentMessages.add(aiMsg)
                    _messages.value = currentMessages.toList()
                    _isLoading.value = false
                    viewModelScope.launch { aiMsg.toEntity()?.let { db.chatMessageDao().insertMessage(it) } }
                    return@launch
                }

                // For complex queries, use the Gemini API with tool calling via system prompt
                val apiKey = ApiKeyManager.getApiKey(getApplication()) ?: ""
                Log.d("ACN_AI", "API Key present: ${apiKey.isNotBlank()}, length: ${apiKey.length}")
                
                val systemPrompt = """
                    You are the ACN Planner AI Assistant for Amrita Chennai students.
                    You are an Omnipresent Agent with access to the entire app.
                    The user is currently viewing the following screen: $currentRoute
                    
                    You have access to the following local tools:
                    - get_today_schedule
                    - get_next_class
                    - get_attendance (args: subjectCode)
                    - get_assignments (args: daysAhead)
                    - get_upcoming_exams
                    - get_announcements (args: limit)
                    - get_semester_progress
                    - get_attendance_what_if (args: subjectCode, attend: boolean)
                    - add_task (args: subjectId:Int, title:String, dueDateString:String(YYYY-MM-DD), priority:String(LOW, MEDIUM, HIGH, URGENT))
                    - mark_task_done (args: assignmentId:Int)
                    - create_event (args: title, dateString(YYYY-MM-DD), type, timeString)
                    - delete_event (args: eventId:Int)
                    - add_subject (args: code, name)
                    - override_attendance (args: code, attended:Int, total:Int)
                    - clear_all_notifications (no args)
                    - cancel_class (args: sessionId, reason)
                    - post_announcement (args: title, body)
                    - generate_study_plan (no args)
                    - save_timetable (args: entries - JSON array string of objects with {day:Int(1=Mon), startTime:String(HH:mm), endTime:String(HH:mm), subjectCode:String, subjectName:String, room:String})
                    - navigate_to (args: route - one of: home, calendar, subjects, announcements, timetable, assignments)
                    - update_theme (args: primaryColorHex: String?, isDarkMode: Boolean?)
                    
                    If the user asks something that can be answered using one of these tools, respond with ONLY a JSON object containing the tool call. For example:
                    {"tool": "get_attendance", "args": {"subjectCode": "19CSE201"}}
                    
                    CRITICAL: If the user asks to navigate, go to, or open a specific screen, you MUST use the `navigate_to` tool.
                    CRITICAL: If the user uploads an image of a timetable or schedule and asks you to parse, save, or extract it, you MUST respond with ONLY the JSON object for the `save_timetable` tool call. DO NOT output any conversational text or markdown before or after the JSON.
                    
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

                // Clean up possible markdown code blocks around JSON
                var cleanResponse = responseText.trim()
                if (cleanResponse.startsWith("```json")) {
                    cleanResponse = cleanResponse.removePrefix("```json").trim()
                } else if (cleanResponse.startsWith("```")) {
                    cleanResponse = cleanResponse.removePrefix("```").trim()
                }
                if (cleanResponse.endsWith("```")) {
                    cleanResponse = cleanResponse.removeSuffix("```").trim()
                }

                // Parse if it's a tool call
                if (cleanResponse.startsWith("{") && cleanResponse.endsWith("}")) {
                    try {
                        val json = org.json.JSONObject(cleanResponse)
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
                            val toolMsg = ChatMessage.ToolCall(tool, "Executing internal tool...")
                            currentMessages.add(toolMsg)
                            currentMessages.add(ChatMessage.Thinking)
                            _messages.value = currentMessages.toList()
                            viewModelScope.launch { toolMsg.toEntity()?.let { db.chatMessageDao().insertMessage(it) } }

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
                            } else if (tool == "navigate_to") {
                                val route = args["route"] ?: "home"
                                _navigationEvents.emit(route)
                                "Navigated successfully to $route"
                            } else if (tool == "update_theme") {
                                val colorHex = if (args.containsKey("primaryColorHex")) args["primaryColorHex"] else null
                                val isDark = if (args.containsKey("isDarkMode")) args["isDarkMode"]?.toBooleanStrictOrNull() else null
                                appPreferences.updateTheme(colorHex, isDark)
                                "App theme updated."
                            } else {
                                val result = toolExecutor.execute(tool, args)
                                // Inject custom UI cards for creation tools
                                if (tool == "create_event" && result.contains("\"success\": true")) {
                                    val title = args["title"] ?: "New Event"
                                    val dateStr = args["dateString"] ?: "Date unknown"
                                    val type = args["type"] ?: "PERSONAL"
                                    val eventMsg = ChatMessage.EventCard(title, dateStr, type)
                                    currentMessages.add(eventMsg)
                                    viewModelScope.launch { eventMsg.toEntity()?.let { db.chatMessageDao().insertMessage(it) } }
                                } else if (tool == "add_task" && result.contains("\"success\": true")) {
                                    val title = args["title"] ?: "New Task"
                                    val due = args["dueDateString"] ?: "Due unknown"
                                    val priority = args["priority"] ?: "MEDIUM"
                                    val taskMsg = ChatMessage.TaskCard(title, due, priority)
                                    currentMessages.add(taskMsg)
                                    viewModelScope.launch { taskMsg.toEntity()?.let { db.chatMessageDao().insertMessage(it) } }
                                }
                                result
                            }
                            
                            // Send tool result back to Gemini for natural language formulation
                            val finalPrompt = "User query: $userMessage\n\nTool Result: $toolResult\n\nExplain this to the user naturally and concisely."
                            val finalResp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                generativeModel.generateContent(finalPrompt)
                            }
                            val finalAiMsg = ChatMessage.Ai(finalResp.text ?: "")
                            currentMessages.remove(ChatMessage.Thinking)
                            currentMessages.add(finalAiMsg)
                            _messages.value = currentMessages.toList()
                            viewModelScope.launch { finalAiMsg.toEntity()?.let { db.chatMessageDao().insertMessage(it) } }
                        } else {
                            val aiMsg = ChatMessage.Ai(cleanResponse)
                            currentMessages.remove(ChatMessage.Thinking)
                            currentMessages.add(aiMsg)
                            _messages.value = currentMessages.toList()
                            viewModelScope.launch { aiMsg.toEntity()?.let { db.chatMessageDao().insertMessage(it) } }
                        }
                    } catch (e: Exception) {
                        Log.e("ACN_AI", "Tool execution failed", e)
                        val errorMsg = ChatMessage.Ai("Tool execution failed: ${e.message}")
                        currentMessages.remove(ChatMessage.Thinking)
                        currentMessages.add(errorMsg)
                        _messages.value = currentMessages.toList()
                        viewModelScope.launch { errorMsg.toEntity()?.let { db.chatMessageDao().insertMessage(it) } }
                    }
                } else {
                    val aiMsg = ChatMessage.Ai(cleanResponse)
                    currentMessages.remove(ChatMessage.Thinking)
                    currentMessages.add(aiMsg)
                    _messages.value = currentMessages.toList()
                    viewModelScope.launch { aiMsg.toEntity()?.let { db.chatMessageDao().insertMessage(it) } }
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
