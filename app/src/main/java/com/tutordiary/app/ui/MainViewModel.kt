package com.tutordiary.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutordiary.app.data.repo.RuleException
import com.tutordiary.app.data.repo.TutorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StartState {
    data object Loading : StartState
    data object NeedsSetup : StartState
    data object Ready : StartState
}

@HiltViewModel
class MainViewModel @Inject constructor(private val repo: TutorRepository) : ViewModel() {

    val start: StateFlow<StartState> = repo.observeProfile()
        .map { if (it == null) StartState.NeedsSetup else StartState.Ready }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StartState.Loading)

    /** Called every time the app comes to the front: adds any monthly fees that became due. */
    fun refreshCharges() {
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
