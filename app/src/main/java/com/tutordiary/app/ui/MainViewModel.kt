package com.tutordiary.app.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutordiary.app.R
import com.tutordiary.app.data.cloud.AuthService
import com.tutordiary.app.data.cloud.SignInResult
import com.tutordiary.app.data.repo.RuleException
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.data.sync.Role
import com.tutordiary.app.data.sync.SessionPrefs
import com.tutordiary.app.data.sync.TutorSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StartState {
    data object Loading : StartState
    data object ChooseRole : StartState
    data class SignIn(val role: Role) : StartState
    data object TutorSetup : StartState
    data object TutorReady : StartState
    data object StudentReady : StartState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: TutorRepository,
    private val session: SessionPrefs,
    private val auth: AuthService,
    private val sync: TutorSync,
) : ViewModel() {

    val authConfigured: Boolean get() = auth.isConfigured

    val start: StateFlow<StartState> = combine(
        session.role, session.offlineTutor, auth.user, repo.observeProfile(),
    ) { role, offline, user, profile ->
        when (role) {
            null -> StartState.ChooseRole
            Role.TUTOR -> when {
                user == null && !(offline && !auth.isConfigured) -> StartState.SignIn(Role.TUTOR)
                profile == null -> StartState.TutorSetup
                else -> StartState.TutorReady
            }
            Role.STUDENT -> if (user == null) StartState.SignIn(Role.STUDENT) else StartState.StudentReady
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StartState.Loading)

    /** Shown under the sign-in button. String resource or null. */
    val signInError = MutableStateFlow<Int?>(null)
    val signingIn = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            start.collect { if (it == StartState.TutorReady) sync.start() else sync.stop() }
        }
    }

    fun chooseRole(role: Role) {
        signInError.value = null
        session.setRole(role)
    }

    fun backToRoleChoice() {
        signInError.value = null
        session.setRole(null)
    }

    fun continueOffline() = session.setOfflineTutor(true)

    fun signIn(activity: Activity) {
        if (signingIn.value) return
        signingIn.value = true
        signInError.value = null
        viewModelScope.launch {
            signInError.value = when (val r = auth.signInWithGoogle(activity)) {
                SignInResult.Ok, SignInResult.Cancelled -> null
                SignInResult.NoGoogleAccount -> R.string.signin_no_account
                SignInResult.NoInternet -> R.string.signin_no_internet
                SignInResult.NotConfigured -> R.string.signin_not_configured
                is SignInResult.Failed -> R.string.signin_failed
            }
            signingIn.value = false
        }
    }

    /** Signs out and goes back to "Who is using this phone?". Records on this phone are kept. */
    fun signOut() {
        viewModelScope.launch {
            sync.stop()
            auth.signOut()
            session.setOfflineTutor(false)
            session.setRole(null)
        }
    }

    /** Called every time the app comes to the front: adds any monthly fees that became due. */
    fun refreshCharges() {
        if (start.value != StartState.TutorReady) return
        viewModelScope.launch {
            try {
                repo.generateDueCharges()
            } catch (e: RuleException) {
                // nothing to show; charges will be retried next time
            }
        }
    }

    fun completeSetup(name: String, business: String, upi: String, defaultEn: String, defaultHi: String, onError: (RuleException) -> Unit) {
        viewModelScope.launch {
            try {
                repo.saveProfile(name, business, upi, defaultEn, defaultHi)
            } catch (e: RuleException) {
                onError(e)
            }
        }
    }
}
