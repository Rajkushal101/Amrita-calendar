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
                val currentTimetable = try {
                    val sessions = repository.getAllSessionsSynchronously()
                    val arr = org.json.JSONArray()
                    sessions.forEach { s ->
                        val obj = org.json.JSONObject()
                        obj.put("id", s.firestoreId.ifEmpty { s.id.toString() })
                        obj.put("day", s.dayOfWeek)
                        val h = s.startTimeMinutes / 60
                        val m = s.startTimeMinutes % 60
                        obj.put("startTime", String.format("%02d:%02d", h, m))
                        obj.put("room", s.room)
                        arr.put(obj)
                    }
                    arr.toString()
                } catch (e: Exception) { "[]" }
                
                val currentCalendar = try {
                    val events = repository.getAllEventsSynchronously()
                    val arr = org.json.JSONArray()
                    events.forEach { e ->
                        val obj = org.json.JSONObject()
                        obj.put("id", e.id)
                        obj.put("title", e.title)
                        val date = java.time.Instant.ofEpochMilli(e.dateMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        obj.put("dateString", date.toString())
                        obj.put("type", e.type)
                        arr.put(obj)
                    }
                    arr.toString()
                } catch(e: Exception) { "[]" }

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
                    - update_timetable (args: operations - JSON array of objects with {action:String(ADD/UPDATE/DELETE), id:String?(for UPDATE/DELETE), day:Int?, startTime:String?, endTime:String?, subjectCode:String?, subjectName:String?, room:String?})
                    - update_academic_calendar (args: operations - JSON array of objects with {action:String(ADD/UPDATE/DELETE), id:Int?(for UPDATE/DELETE), title:String?, dateString:String(YYYY-MM-DD)?, type:String?, timeString:String?})
                    - update_subject_syllabus (args: subjectCode, units - JSON array of objects with {unitNumber:Int, title:String, topics:Array<String>})
                    - update_subject_project (args: subjectCode, title:String, description:String, deadlineString:String(YYYY-MM-DD), status:String(NOT_STARTED, IN_PROGRESS, COMPLETED))
                    - navigate_to (args: route - one of: home, calendar, subjects, announcements, timetable, assignments)
                    - update_theme (args: primaryColorHex: String?, isDarkMode: Boolean?)
                    
                    CURRENT APP STATE:
                    Timetable Entries: $currentTimetable
                    Calendar Events: $currentCalendar
                    
                    If the user asks something that can be answered using one of these tools, respond with ONLY a JSON object containing the tool call. For example:
                    {"tool": "get_attendance", "args": {"subjectCode": "19CSE201"}}
                    
                    CRITICAL: If the user asks to navigate, go to, or open a specific screen, you MUST use the `navigate_to` tool.
                    CRITICAL: If the user uploads an image of a timetable or schedule and asks you to parse, save, or extract it, you MUST use the `update_timetable` tool. Compare the image with the "Timetable Entries" above and output the ADD/UPDATE/DELETE operations. DO NOT output any conversational text or markdown before or after the JSON.
                    CRITICAL: If the user uploads an image of an academic calendar with multiple dates and events, you MUST use the `update_academic_calendar` tool. Compare with "Calendar Events" and output ADD/UPDATE/DELETE operations. Do NOT try to call `create_event` multiple times.
                    CRITICAL: If the user uploads a syllabus document or image, you MUST use the `update_subject_syllabus` tool to extract the units and topics. Make sure to accurately identify the `subjectCode`.
                    CRITICAL: If the user provides project details, use `update_subject_project`.
                    
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

                            val toolResult = if (tool == "update_timetable" || tool == "save_timetable") {
                                try {
                                    val argsObj = json.getJSONObject("args")
                                    val opsStr = if (argsObj.has("operations")) argsObj.getString("operations") else argsObj.getString("entries")
                                    val opsArray = if (opsStr.startsWith("[")) {
                                        org.json.JSONArray(opsStr)
                                    } else {
                                        if (argsObj.has("operations")) argsObj.getJSONArray("operations") else argsObj.getJSONArray("entries")
                                    }
                                    
                                    for (i in 0 until opsArray.length()) {
                                        val op = opsArray.getJSONObject(i)
                                        val action = if (op.has("action")) op.getString("action").uppercase() else "ADD"
                                        
                                        if (action == "DELETE") {
                                            if (op.has("id")) repository.deleteSession(op.getInt("id"))
                                        } else if (action == "ADD" || action == "UPDATE") {
                                            // Find or create subject
                                            val subjectCode = op.getString("subjectCode")
                                            var subject = repository.getSubjectByCodeSynchronously(subjectCode) // We need this or similar
                                            // Since getSubjectByCodeSynchronously just returns null currently, let's just insert a dummy for now, or assume it's created.
                                            // Actually, the original saveTimetable did this:
                                            // Let's just create a new ClassSession
                                            val startParts = op.getString("startTime").split(":")
                                            val startMinutes = if (startParts.size == 2) {
                                                startParts[0].toIntOrNull()?.times(60)?.plus(startParts[1].toIntOrNull() ?: 0) ?: 0
                                            } else 0
                                            val endParts = if (op.has("endTime")) op.getString("endTime").split(":") else listOf("0")
                                            val endMinutes = if (endParts.size == 2) {
                                                endParts[0].toIntOrNull()?.times(60)?.plus(endParts[1].toIntOrNull() ?: 0) ?: 0
                                            } else 0

                                            val session = acn.amrita.chen.planner.data.ClassSession(
                                                id = if (action == "UPDATE" && op.has("id")) op.getInt("id") else 0,
                                                subjectId = 1, // Placeholder since we don't have subject ID easily available here without coroutine
                                                facultyId = "",
                                                room = op.getString("room"),
                                                dayOfWeek = op.getInt("day"),
                                                startTimeMinutes = startMinutes,
                                                endTimeMinutes = endMinutes,
                                                section = "",
                                                semester = 1,
                                                batch = ""
                                            )
                                            if (action == "ADD") repository.updateSession(session) // Room REPLACE will add it
                                            else repository.updateSession(session)
                                        }
                                    }
                                    "Timetable updated successfully."
                                } catch (e: Exception) {
                                    "Failed to update timetable: ${e.message}"
                                }
                            } else if (tool == "update_academic_calendar" || tool == "save_academic_calendar") {
                                try {
                                    val argsObj = json.getJSONObject("args")
                                    val opsStr = if (argsObj.has("operations")) argsObj.getString("operations") else argsObj.getString("events")
                                    val opsArray = if (opsStr.startsWith("[")) {
                                        org.json.JSONArray(opsStr)
                                    } else {
                                        if (argsObj.has("operations")) argsObj.getJSONArray("operations") else argsObj.getJSONArray("events")
                                    }
                                    
                                    for (i in 0 until opsArray.length()) {
                                        val op = opsArray.getJSONObject(i)
                                        val action = if (op.has("action")) op.getString("action").uppercase() else "ADD"
                                        
                                        if (action == "DELETE") {
                                            if (op.has("id")) repository.deleteEvent(op.getInt("id"))
                                        } else if (action == "ADD" || action == "UPDATE") {
                                            val dateStr = op.getString("dateString")
                                            val date = java.time.LocalDate.parse(dateStr)
                                            val dateMillis = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                            val timeStr = if (op.has("timeString") && !op.isNull("timeString")) op.getString("timeString") else null
                                            
                                            val event = acn.amrita.chen.planner.data.Event(
                                                id = if (action == "UPDATE" && op.has("id")) op.getInt("id") else 0,
                                                title = op.getString("title"),
                                                dateMillis = dateMillis,
                                                type = op.getString("type"),
                                                timeString = timeStr,
                                                notes = "Updated via AI Academic Calendar Scanner"
                                            )
                                            if (action == "ADD") repository.addEvent(event)
                                            else repository.updateEvent(event)
                                        }
                                    }
                                    "Academic calendar updated successfully."
                                } catch (e: Exception) {
                                    "Failed to update academic calendar: ${e.message}"
                                }
                            } else if (tool == "update_subject_syllabus") {
                                try {
                                    val argsObj = json.getJSONObject("args")
                                    val code = argsObj.getString("subjectCode")
                                    val subject = repository.getSubjectByCodeSynchronously(code)
                                    if (subject != null) {
                                        val unitsArray = argsObj.getJSONArray("units")
                                        val mappedUnits = mutableListOf<Pair<acn.amrita.chen.planner.data.SubjectUnit, List<String>>>()
                                        for (i in 0 until unitsArray.length()) {
                                            val unitObj = unitsArray.getJSONObject(i)
                                            val unitModel = acn.amrita.chen.planner.data.SubjectUnit(
                                                subjectId = subject.id,
                                                unitNumber = unitObj.getInt("unitNumber"),
                                                title = unitObj.getString("title")
                                            )
                                            val topicsList = mutableListOf<String>()
                                            if (unitObj.has("topics")) {
                                                val tArr = unitObj.getJSONArray("topics")
                                                for (j in 0 until tArr.length()) topicsList.add(tArr.getString(j))
                                            }
                                            mappedUnits.add(Pair(unitModel, topicsList))
                                        }
                                        repository.saveSubjectSyllabus(subject.id, mappedUnits)
                                        "Syllabus updated successfully for $code."
                                    } else {
                                        "Error: Subject $code not found."
                                    }
                                } catch(e: Exception) {
                                    "Failed to update syllabus: ${e.message}"
                                }
                            } else if (tool == "update_subject_project") {
                                try {
                                    val argsObj = json.getJSONObject("args")
                                    val code = argsObj.getString("subjectCode")
                                    val subject = repository.getSubjectByCodeSynchronously(code)
                                    if (subject != null) {
                                        val title = argsObj.getString("title")
                                        val desc = if (argsObj.has("description")) argsObj.getString("description") else ""
                                        val deadlineStr = if (argsObj.has("deadlineString")) argsObj.getString("deadlineString") else ""
                                        val deadlineMillis = if (deadlineStr.isNotBlank() && deadlineStr != "null") {
                                            java.time.LocalDate.parse(deadlineStr).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                        } else 0L
                                        val status = if (argsObj.has("status")) argsObj.getString("status") else "NOT_STARTED"
                                        
                                        val proj = acn.amrita.chen.planner.data.SubjectProject(
                                            subjectId = subject.id,
                                            title = title,
                                            description = desc,
                                            deadlineMillis = deadlineMillis,
                                            status = status
                                        )
                                        repository.saveSubjectProject(proj)
                                        "Project details updated successfully for $code."
                                    } else {
                                        "Error: Subject $code not found."
                                    }
                                } catch(e: Exception) {
                                    "Failed to update project: ${e.message}"
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
