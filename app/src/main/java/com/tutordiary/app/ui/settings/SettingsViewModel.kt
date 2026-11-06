package com.tutordiary.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutordiary.app.R
import com.tutordiary.app.data.BackupPrefs
import com.tutordiary.app.data.backup.AllData
import com.tutordiary.app.data.backup.BackupCodec
import com.tutordiary.app.data.backup.BackupProblem
import com.tutordiary.app.data.backup.DecodeResult
import com.tutordiary.app.data.db.TutorProfileEntity
import com.tutordiary.app.data.repo.RuleException
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.data.repo.ledgers
import com.tutordiary.app.domain.Csv
import com.tutordiary.app.domain.FeeStatus
import com.tutordiary.app.ui.common.ruleMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Column titles and words for CSV files, taken from string resources by the screen. */
data class CsvLabels(
    val studentsHeader: List<String>,
    val balancesHeader: List<String>,
    val active: String,
    val archived: String,
    val status: Map<FeeStatus, String>,
)

data class PendingRestore(val data: AllData, val students: Int, val payments: Int, val createdOn: LocalDate)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: TutorRepository,
    private val auth: com.tutordiary.app.data.cloud.AuthService,
    private val backupPrefs: BackupPrefs,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val profile: StateFlow<TutorProfileEntity?> = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lastBackup: StateFlow<Long?> = backupPrefs.lastBackupEpochDay

    /** One-off message (string resource) for a snackbar. */
    val message = MutableStateFlow<Int?>(null)
    val pendingRestore = MutableStateFlow<PendingRestore?>(null)

    fun today(): LocalDate = repo.today()

    val accountEmail: String? get() = auth.user.value?.let { it.email ?: it.name ?: "" }

    fun saveProfile(name: String, business: String, upi: String, defaultEn: String, defaultHi: String) {
        viewModelScope.launch {
            try {
                repo.saveProfile(name, business, upi, defaultEn, defaultHi)
                message.value = R.string.saved
            } catch (e: RuleException) {
                message.value = ruleMessage(e.rule)
            }
        }
    }

    fun saveTemplates(en: String, hi: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.saveTemplates(en, hi)
                onDone()
            } catch (e: RuleException) {
                message.value = ruleMessage(e.rule)
            }
        }
    }

    private suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Could not open file")
    }

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            message.value = try {
                val text = BackupCodec.encode(repo.readAll(), repo.today().toEpochDay())
                writeText(uri, text)
                backupPrefs.markBackup(repo.today().toEpochDay())
                R.string.backup_done
            } catch (e: Exception) {
                R.string.backup_failed
            }
        }
    }

    /** Step 1: read and check the file. Nothing changes yet. */
    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            val text = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // Guard against picking a huge unrelated file.
                        val bytes = input.readBytes()
                        if (bytes.size > 50 * 1024 * 1024) null else bytes.toString(Charsets.UTF_8)
                    }
                }
            } catch (e: Exception) {
                null
            }
            if (text == null) { message.value = R.string.restore_bad_file; return@launch }
            when (val result = withContext(Dispatchers.Default) { BackupCodec.decode(text) }) {
                is DecodeResult.Bad -> message.value = when (result.problem) {
                    BackupProblem.NotJson, BackupProblem.WrongApp -> R.string.restore_bad_file
                    is BackupProblem.NewerVersion -> R.string.restore_newer
                    is BackupProblem.Invalid -> R.string.restore_invalid
                }
                is DecodeResult.Ok -> {
                    val created = Regex("\"createdAtEpochDay\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull()
                    pendingRestore.value = PendingRestore(
                        result.data,
                        students = result.data.students.size,
                        payments = result.data.payments.size,
                        createdOn = LocalDate.ofEpochDay(created ?: repo.today().toEpochDay()),
                    )
                }
            }
        }
    }

    /** Step 2: after the tutor confirms, replace everything in one transaction. */
    fun confirmRestore() {
        val pending = pendingRestore.value ?: return
        pendingRestore.value = null
        viewModelScope.launch {
            message.value = try {
                repo.replaceAll(pending.data)
                repo.generateDueCharges()
                R.string.restore_done
            } catch (e: Exception) {
                R.string.restore_failed
            }
        }
    }

    fun exportStudents(uri: Uri, labels: CsvLabels) {
        viewModelScope.launch {
            message.value = try {
                val data = repo.readAll()
                val batchNames = data.batches.associate { it.id to it.name }
                val rows = data.students.sortedBy { it.name.lowercase() }.map { s ->
                    listOf(
                        s.name, s.parentName, s.phone, s.batchId?.let(batchNames::get).orEmpty(),
                        s.monthlyFee.toString(), s.dueDay.toString(), csvDate(s.joinDate),
                        if (s.archivedOn == null) labels.active else labels.archived, s.notes.orEmpty(),
                    )
                }
                writeText(uri, Csv.build(labels.studentsHeader, rows))
                R.string.export_done
            } catch (e: Exception) {
                R.string.export_failed
            }
        }
    }

    fun exportBalances(uri: Uri, labels: CsvLabels) {
        viewModelScope.launch {
            message.value = try {
                val data = repo.observeFeeData().first()
                val ledgers = data.ledgers(repo.today())
                val rows = data.students.sortedBy { it.name.lowercase() }.mapNotNull { s ->
                    val l = ledgers[s.id] ?: return@mapNotNull null
                    listOf(
                        s.name, s.parentName, s.phone,
                        l.totalCharged.toString(), l.totalWaived.toString(), l.totalPaid.toString(),
                        l.amountDue.toString(), l.credit.toString(), l.unpaidMonths.size.toString(),
                        labels.status[l.status].orEmpty(),
                    )
                }
                writeText(uri, Csv.build(labels.balancesHeader, rows))
                R.string.export_done
            } catch (e: Exception) {
                R.string.export_failed
            }
        }
    }

    private fun csvDate(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}
