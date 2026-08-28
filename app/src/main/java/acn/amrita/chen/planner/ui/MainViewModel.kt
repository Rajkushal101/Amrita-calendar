package acn.amrita.chen.planner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import acn.amrita.chen.planner.data.AcnRepository
import acn.amrita.chen.planner.data.AppDatabase
import acn.amrita.chen.planner.data.Event
import acn.amrita.chen.planner.data.Subject
import acn.amrita.chen.planner.data.Announcement
import acn.amrita.chen.planner.data.UserProfile
import com.google.firebase.auth.FirebaseAuth
import acn.amrita.chen.planner.ai.AttendanceIntelligence
import acn.amrita.chen.planner.ai.AttendanceAnalysis
import acn.amrita.chen.planner.data.CampusChange
import acn.amrita.chen.planner.data.ChangeType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    val repository = AcnRepository(db)
    private val eventDao = db.eventDao()
    private val classSessionDao = db.classSessionDao()
    private val assignmentDao = db.assignmentDao()
    private val subjectDao = db.subjectDao()

    private val prefs = application.getSharedPreferences("acn_prefs", android.content.Context.MODE_PRIVATE)
    private val auth = FirebaseAuth.getInstance()

    init {
        repository.startAnnouncementsSync()
        repository.startClassSessionsSync()
        
        // Ensure user is authenticated anonymously
        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: ""
                    syncUserProfileToFirestore(uid)
                }
            }
        }
        
        // Schedule Background Sync
        val syncWorkRequest = androidx.work.PeriodicWorkRequestBuilder<acn.amrita.chen.planner.worker.AumsSyncWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        ).build()
        androidx.work.WorkManager.getInstance(application).enqueueUniquePeriodicWork(
            "AumsSyncWorker",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest
        )
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopAnnouncementsSync()
        repository.stopClassSessionsSync()
    }

    private val _hasRole = MutableStateFlow(prefs.contains("user_role"))
    val hasRole: StateFlow<Boolean> = _hasRole

    private val _userRole = MutableStateFlow(prefs.getString("user_role", "STUDENT") ?: "STUDENT")
    val userRole: StateFlow<String> = _userRole

    private val _showAddEventDialog = MutableStateFlow(false)
    val showAddEventDialog: StateFlow<Boolean> = _showAddEventDialog

    fun saveRole(role: String) {
        prefs.edit().putString("user_role", role).apply()
        _userRole.value = role
        _hasRole.value = true
        
        viewModelScope.launch {
            // Update profile with role
            val profile = UserProfile(id = 1, role = role)
            db.userProfileDao().insertUserProfile(profile)
            auth.currentUser?.uid?.let { syncUserProfileToFirestore(it) }
        }
    }
    
    private fun syncUserProfileToFirestore(uid: String) {
        viewModelScope.launch {
            val profile = db.userProfileDao().getProfileSync() ?: UserProfile(id = 1)
            val updatedProfile = profile.copy(firestoreUid = uid)
            db.userProfileDao().insertUserProfile(updatedProfile)
            
            // Sync to firestore
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            firestore.collection("users").document(uid).set(updatedProfile)
        }
    }

    fun showAddEventDialog() {
        _showAddEventDialog.value = true
    }

    fun hideAddEventDialog() {
        _showAddEventDialog.value = false
    }

    fun syncAumsAttendance(html: String) {
        viewModelScope.launch {
            repository.syncScrapedAttendance(html)
        }
    }

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val allEvents: StateFlow<List<Event>> = eventDao.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredEvents = combine(allEvents, _searchQuery) { events, query ->
        if (query.isBlank()) events else events.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todaySessions: StateFlow<List<acn.amrita.chen.planner.data.ClassSession>> = classSessionDao.getSessionsForDay(LocalDate.now().dayOfWeek.value)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nextClass = todaySessions.map { sessions ->
        val nowMinutes = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        sessions.firstOrNull { it.startTimeMinutes > nowMinutes }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun getSessionsForDay(dayOfWeek: Int) = classSessionDao.getSessionsForDay(dayOfWeek)

    val subjects: StateFlow<List<Subject>> = subjectDao.getAllSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())



    val pendingAssignments: StateFlow<List<acn.amrita.chen.planner.data.Assignment>> = repository.getPendingAssignments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attendanceWarnings = subjects.map { subs ->
        subs.map { AttendanceIntelligence.analyze(it) }
            .filter { it.status != acn.amrita.chen.planner.ai.AttendanceStatus.SAFE }
            .sortedBy { it.currentPercentage }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentChanges: StateFlow<List<CampusChange>> = MutableStateFlow(
        listOf(
            CampusChange(ChangeType.CLASS_CANCELLED, "CYS 10AM class cancelled", System.currentTimeMillis() - 3600000),
            CampusChange(ChangeType.ASSIGNMENT_DEADLINE_CHANGED, "AI assignment deadline moved to Sunday", System.currentTimeMillis() - 86400000),
            CampusChange(ChangeType.NEW_ANNOUNCEMENT, "1 new faculty announcement", System.currentTimeMillis() - 7200000)
        )
    ).asStateFlow()

    fun updateAssignmentStatus(assignmentId: Int, status: acn.amrita.chen.planner.data.AssignmentStatus) {
        viewModelScope.launch {
            repository.updateAssignmentStatus(assignmentId, status)
        }
    }

    val announcements: StateFlow<List<Announcement>> = repository.getAllAnnouncements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun analyzeAttendance(subject: Subject): AcnRepository.AttendanceAnalysis =
        repository.analyzeAttendance(subject)

    fun getSemesterProgress(): AcnRepository.SemesterProgress =
        repository.getSemesterProgress()

    fun nextMonth() {
        _currentMonth.value = _currentMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        _currentMonth.value = _currentMonth.value.minusMonths(1)
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        if (date.monthValue != _currentMonth.value.monthValue || date.year != _currentMonth.value.year) {
            _currentMonth.value = YearMonth.of(date.year, date.monthValue)
        }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addEvent(title: String, date: LocalDate, type: String, timeString: String?, notes: String?, reminderType: String) {
        viewModelScope.launch {
            val reminderTimeMillis = calculateReminderMillis(date, reminderType, timeString)
            val event = Event(
                title = title,
                dateMillis = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                type = type,
                timeString = timeString,
                notes = notes,
                hasReminder = reminderTimeMillis != null,
                reminderTimeMillis = reminderTimeMillis,
                reminderType = reminderType
            )
            val id = eventDao.insertEvent(event)
            if (reminderTimeMillis != null) {
                scheduleReminder(id.toInt(), title, reminderTimeMillis)
            }
        }
    }
    
    private fun calculateReminderMillis(date: LocalDate, reminderType: String, timeString: String?): Long? {
        if (reminderType == "none") return null
        var reminderDate = date
        when (reminderType) {
            "1d" -> reminderDate = date.minusDays(1)
            "3d" -> reminderDate = date.minusDays(3)
            "1w" -> reminderDate = date.minusWeeks(1)
        }
        val time = if (!timeString.isNullOrBlank()) {
            try { java.time.LocalTime.parse(timeString) } catch (e: Exception) { java.time.LocalTime.of(9, 0) }
        } else {
            java.time.LocalTime.of(9, 0)
        }
        return reminderDate.atTime(time).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun scheduleReminder(eventId: Int, title: String, timeMillis: Long) {
        val alarmManager = getApplication<Application>().getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(getApplication(), acn.amrita.chen.planner.receiver.ReminderReceiver::class.java).apply {
            putExtra("event_title", title)
            putExtra("event_id", eventId)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            getApplication(),
            eventId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                timeMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }


}
