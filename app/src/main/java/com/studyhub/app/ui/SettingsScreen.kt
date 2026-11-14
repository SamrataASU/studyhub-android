package com.studyhub.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyhub.app.MainViewModel
import com.studyhub.app.R
import com.studyhub.app.UiState
import com.studyhub.app.data.Fmt

@Composable
fun SettingsScreen(state: UiState, vm: MainViewModel, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(state.name) }
    var feed by rememberSaveable { mutableStateOf(state.feed) }
    val askNotifications = rememberNotificationPermission()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
        ) {
            Text("Settings", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (name.trim() != state.name) vm.setName(name)
                onClose()
            }) { Text("Done", color = Palette.Violet, fontWeight = FontWeight.Bold) }
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Label("YOU")
            GlassCard {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Your name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Label("CANVAS")
            GlassCard {
                OutlinedTextField(
                    value = feed,
                    onValueChange = { feed = it },
                    singleLine = true,
                    label = { Text("Canvas calendar feed link") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.setFeed(feed) },
                    enabled = feed.trim() != state.feed,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Violet),
                ) { Text("Save and sync") }
                Spacer(Modifier.height(8.dp))
                when {
                    state.error != null -> StatusLine(Icons.Filled.Warning, Palette.Amber, state.error)
                    state.feed.isNotBlank() -> StatusLine(Icons.Filled.CheckCircle, Palette.Mint, "${state.items.size} items from Canvas")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "In Canvas, open Calendar, tap “Calendar Feed” and copy the link. Treat it like a password.",
                    color = Palette.Muted, fontSize = 12.sp,
                )
            }

            Label("NOTIFICATIONS")
            GlassCard {
                ToggleRow("Deadline reminders", "The day before and 3 hours before each deadline", state.reminders) {
                    if (it) askNotifications()
                    vm.setReminders(it)
                }
                HorizontalDivider(color = Palette.Stroke, modifier = Modifier.padding(vertical = 8.dp))
                ToggleRow("Daily finance news", "A morning reminder at about 8 AM", state.dailyNews) {
                    if (it) askNotifications()
                    vm.setDailyNews(it)
                }
            }

            GlassCard {
                TextButton(onClick = vm::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Palette.Violet)
                    Spacer(Modifier.width(8.dp))
                    Text("Sync now", color = Palette.Violet)
                }
                state.lastSynced?.let {
                    Text("Last synced ${Fmt.clock(it)}", color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }

            Text(
                "Your data stays on this phone. The app only downloads your Canvas calendar and public news headlines.",
                color = Palette.Muted, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Palette.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 13.sp)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(subtitle, color = Palette.Muted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Violet),
        )
    }
}

/** Returns a function that asks for notification permission (Android 13+). */
@Composable
fun rememberNotificationPermission(): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** First launch: ask for a name and the Canvas link. */
@Composable
fun OnboardingScreen(onDone: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var feed by rememberSaveable { mutableStateOf("") }
    val askNotifications = rememberNotificationPermission()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)),
        )
        Text("Welcome", color = Palette.Violet, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text("All your class deadlines, in one place", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
        Text(
            "Countdowns, reminders, widgets and daily finance news, all from your Canvas calendar.",
            color = Palette.Muted, fontSize = 15.sp,
        )

        GlassCard {
            Text("What should we call you?", color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Your first name") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        GlassCard {
            Text("Your Canvas calendar link", color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "1. Open Canvas in a web browser and sign in.\n" +
                    "2. Go to Calendar.\n" +
                    "3. Tap “Calendar Feed” (bottom right) and copy the link.\n" +
                    "4. Paste it below.",
                color = Palette.Muted, fontSize = 13.sp, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = feed,
                onValueChange = { feed = it },
                singleLine = true,
                placeholder = { Text("https://…/feeds/calendars/…") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = {
                askNotifications()
                onDone(name, feed)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Violet),
        ) { Text(if (feed.isBlank()) "Skip for now" else "Get started", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}
