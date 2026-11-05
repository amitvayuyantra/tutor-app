package com.tutordiary.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tutordiary.app.ui.AppNav
import com.tutordiary.app.ui.MainViewModel
import com.tutordiary.app.ui.StartState
import com.tutordiary.app.ui.common.LoadingBox
import com.tutordiary.app.ui.setup.SetupScreen
import com.tutordiary.app.ui.theme.TutorDiaryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val defaultEn = getString(R.string.default_template_en)
        val defaultHi = getString(R.string.default_template_hi)
        setContent {
            TutorDiaryTheme {
                // Every time the app is opened or comes back, add any monthly fees that became due.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshCharges() }
                val start by vm.start.collectAsStateWithLifecycle()
                when (start) {
                    StartState.Loading -> LoadingBox()
                    StartState.NeedsSetup -> SetupScreen { name, business, upi, onError ->
                        vm.completeSetup(name, business, upi, defaultEn, defaultHi, onError)
                    }
                    StartState.Ready -> AppNav()
                }
            }
        }
    }
}
