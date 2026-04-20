package com.reminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.reminder.notifications.DailyPreviewScheduler
import com.reminder.notifications.NotificationHelper
import com.reminder.notifications.ReminderScheduler
import com.reminder.sync.SyncManager
import com.reminder.ui.ReminderApp
import com.reminder.ui.ReminderViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: ReminderViewModel by viewModels()

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* user choice */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.ensureChannel(this)
        SyncManager.get(this).start()
        DailyPreviewScheduler.scheduleNext(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val repo = com.reminder.data.ReminderRepository(this@MainActivity)
            for (r in repo.activeReminders()) {
                ReminderScheduler.scheduleNext(this@MainActivity, r)
            }
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0A1628),
                    surface = Color(0xFF0A1628),
                    primary = Color(0xFF22D3EE),
                    onBackground = Color(0xFFE8F0FA),
                    onSurface = Color(0xFFE8F0FA),
                ),
            ) {
                Surface { ReminderApp(viewModel) }
            }
        }
    }
}
