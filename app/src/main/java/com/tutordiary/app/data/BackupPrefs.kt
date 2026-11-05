package com.tutordiary.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers when the last backup file was made, so the app can warn if there is none. */
@Singleton
class BackupPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
    private val _lastBackup = MutableStateFlow(prefs.getLong(KEY, -1L).takeIf { it >= 0 })
    val lastBackupEpochDay: StateFlow<Long?> = _lastBackup

    fun markBackup(epochDay: Long) {
        prefs.edit().putLong(KEY, epochDay).apply()
        _lastBackup.value = epochDay
    }

    private companion object { const val KEY = "last_backup_epoch_day" }
}
