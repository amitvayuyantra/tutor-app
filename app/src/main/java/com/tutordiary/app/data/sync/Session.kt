package com.tutordiary.app.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class Role { TUTOR, STUDENT }

/** Remembers whether this phone is used by the tutor or by a student/parent. */
@Singleton
class SessionPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    private val _role = MutableStateFlow(prefs.getString(KEY_ROLE, null)?.let { runCatching { Role.valueOf(it) }.getOrNull() })
    val role: StateFlow<Role?> = _role

    /** Only possible in a build without online features: tutor uses the app with no account. */
    private val _offlineTutor = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE, false))
    val offlineTutor: StateFlow<Boolean> = _offlineTutor

    fun setRole(role: Role?) {
        prefs.edit().putString(KEY_ROLE, role?.name).apply()
        _role.value = role
    }

    fun setOfflineTutor(on: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE, on).apply()
        _offlineTutor.value = on
    }

    private companion object {
        const val KEY_ROLE = "role"
        const val KEY_OFFLINE = "offline_tutor"
    }
}
