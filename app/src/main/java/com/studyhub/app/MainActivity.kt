package com.studyhub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.studyhub.app.ui.StudyHubApp
import com.studyhub.app.work.Notifier
import com.studyhub.app.work.RefreshWorker

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.createChannels(this)
        RefreshWorker.schedule(this)
        setContent { StudyHubApp(vm) }
    }

    override fun onResume() {
        super.onResume()
        if (vm.state.value.onboarded) vm.refresh()
    }
}
