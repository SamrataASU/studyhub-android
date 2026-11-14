package com.studyhub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.LaunchedEffect
import com.studyhub.app.data.Store
import com.studyhub.app.ui.Palette
import com.studyhub.app.ui.ThemeChoice
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
        Palette.theme = ThemeChoice.from(Store.theme(this))
        setContent {
            // Dark status-bar icons on the Light theme, white icons on the others.
            val light = Palette.theme.isLight
            LaunchedEffect(light) {
                val bars = if (light) SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            StudyHubApp(vm)
        }
    }

    override fun onResume() {
        super.onResume()
        if (vm.state.value.onboarded) vm.refresh()
    }
}
