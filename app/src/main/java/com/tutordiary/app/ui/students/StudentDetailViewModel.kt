package com.tutordiary.app.ui.students

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutordiary.app.data.db.ChargeEntity
import com.tutordiary.app.data.db.PaymentEntity
import com.tutordiary.app.data.db.StudentEntity
import com.tutordiary.app.data.db.TutorProfileEntity
import com.tutordiary.app.data.db.WaiverEntity
import com.tutordiary.app.data.repo.RuleException
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.data.repo.ledgers
import com.tutordiary.app.domain.LedgerSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class MonthLine(val charge: ChargeEntity, val waiver: WaiverEntity?, val remaining: Long)

data class AttendanceSummary(val month: YearMonth, val present: Int, val absent: Int)

data class StudentDetailState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val student: StudentEntity? = null,
    val batchName: String? = null,
    val ledger: LedgerSummary? = null,
    val months: List<MonthLine> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val profile: TutorProfileEntity? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StudentDetailViewModel @Inject constructor(
    private val repo: TutorRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val studentId: Long = savedState.get<Long>("studentId") ?: 0L

    val attendanceMonth = MutableStateFlow(YearMonth.from(repo.today()))

    val state: StateFlow<StudentDetailState> = combine(
        repo.observeStudent(studentId), repo.observeFeeData(), repo.observeBatches(), repo.observeProfile(),
    ) { student, data, batches, profile ->
        if (student == null) return@combine StudentDetailState(loading = false, missing = true)
        val ledger = data.ledgers(repo.today())[studentId]
        val remainingByMonth = ledger?.unpaidMonths?.associate { it.month to it.remaining }.orEmpty()
        val waiverByMonth = data.waivers.filter { it.studentId == studentId }.associateBy { it.month }
        StudentDetailState(
            loading = false,
            student = student,
            batchName = student.batchId?.let { id -> batches.firstOrNull { it.id == id }?.name },
            ledger = ledger,
            months = data.charges.filter { it.studentId == studentId }
                .sortedByDescending { it.month }
                .map { MonthLine(it, waiverByMonth[it.month], remainingByMonth[it.month] ?: 0) },
            payments = data.payments.filter { it.studentId == studentId },
            profile = profile,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentDetailState())

    val attendance: StateFlow<AttendanceSummary?> = attendanceMonth.flatMapLatest { m ->
        repo.observeAttendance(studentId, m.atDay(1), m.atEndOfMonth()).map { recs ->
            AttendanceSummary(m, present = recs.count { it.present }, absent = recs.count { !it.present })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Shown once as a message, then cleared. */
    val message = MutableStateFlow<Int?>(null)

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: RuleException) { message.value = com.tutordiary.app.ui.common.ruleMessage(e.rule) }
        }
    }

    fun shiftAttendanceMonth(by: Long) { attendanceMonth.value = attendanceMonth.value.plusMonths(by) }
    fun archive() = launchSafely { repo.archiveStudent(studentId) }
    fun unarchive() = launchSafely { repo.unarchiveStudent(studentId) }
    fun deleteForever(onDone: () -> Unit) = launchSafely { repo.deleteStudentPermanently(studentId); onDone() }
    fun markReminderOpened() = launchSafely { repo.markReminderOpened(studentId) }
    fun deleteWaiver(id: Long) = launchSafely { repo.deleteWaiver(id) }
}
