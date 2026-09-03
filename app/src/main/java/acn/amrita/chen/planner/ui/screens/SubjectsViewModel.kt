package acn.amrita.chen.planner.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import acn.amrita.chen.planner.data.AcnRepository
import acn.amrita.chen.planner.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ── UI Models ───────────────────────────────────────────
data class SubjectUi(
    val id: Int,
    val name: String,
    val code: String,
    val faculty: String = "",
    val attended: Int,
    val total: Int,
    val semester: Int,
    val requiredPct: Float = 75f
) {
    val percentage: Float get() = if (total == 0) 100f else attended.toFloat() / total * 100f
    val status: AttStatus get() = when {
        percentage >= 85f -> AttStatus.SAFE
        percentage >= 75f -> AttStatus.WARN
        else              -> AttStatus.DANGER
    }
    val canMiss: Int get() {
        if (status == AttStatus.DANGER) return 0
        var miss = 0
        while ((attended.toFloat() / (total + miss + 1)) * 100f >= requiredPct) miss++
        return miss
    }
    val classesToRecover: Int get() {
        if (percentage >= requiredPct) return 0
        var extra = 0
        while ((attended + extra).toFloat() / (total + extra) * 100f < requiredPct) extra++
        return extra
    }
}

class SubjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = AcnRepository(db)
    
    private val _selectedSemester = MutableStateFlow(3) // Default to semester 3 or from profile
    val selectedSemester: StateFlow<Int> = _selectedSemester
    
    fun setSemester(sem: Int) {
        _selectedSemester.value = sem
    }
    
    val subjects: StateFlow<List<SubjectUi>> = kotlinx.coroutines.flow.combine(
        repository.getAllSubjects(),
        _selectedSemester
    ) { subs, sem ->
        subs.filter { it.semester == sem || it.semester == 0 }.map {
            SubjectUi(
                id = it.id,
                name = it.name,
                code = it.code,
                faculty = it.faculty,
                attended = it.attendedClasses,
                total = it.totalClasses,
                semester = it.semester
            )
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSubjectUnits = MutableStateFlow<List<acn.amrita.chen.planner.data.SubjectUnit>>(emptyList())
    val selectedSubjectUnits: StateFlow<List<acn.amrita.chen.planner.data.SubjectUnit>> = _selectedSubjectUnits

    private val _selectedSubjectTopics = MutableStateFlow<Map<Int, List<acn.amrita.chen.planner.data.SubjectTopic>>>(emptyMap())
    val selectedSubjectTopics: StateFlow<Map<Int, List<acn.amrita.chen.planner.data.SubjectTopic>>> = _selectedSubjectTopics

    private val _selectedSubjectProject = MutableStateFlow<acn.amrita.chen.planner.data.SubjectProject?>(null)
    val selectedSubjectProject: StateFlow<acn.amrita.chen.planner.data.SubjectProject?> = _selectedSubjectProject

    fun loadSubjectDetails(subjectId: Int) {
        viewModelScope.launch {
            repository.getUnitsForSubject(subjectId).collectLatest { units ->
                _selectedSubjectUnits.value = units
                val topicsMap = mutableMapOf<Int, List<acn.amrita.chen.planner.data.SubjectTopic>>()
                for (unit in units) {
                    val topics = repository.getTopicsForUnitsSync(listOf(unit.id))
                    topicsMap[unit.id] = topics
                }
                _selectedSubjectTopics.value = topicsMap
            }
        }
        viewModelScope.launch {
            repository.getProjectForSubject(subjectId).collectLatest {
                _selectedSubjectProject.value = it
            }
        }
    }
}
