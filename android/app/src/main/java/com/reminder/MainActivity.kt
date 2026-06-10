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
import com.reminder.notifications.DailyPreviewReceiver
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

        // Debug-only verification hook: `adb shell am start -n com.reminder/.MainActivity
        // --ez fire_preview true` fires the daily preview immediately so it can be checked
        // after a deploy. No-op in release builds.
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("fire_preview", false) == true) {
            CoroutineScope(Dispatchers.IO).launch {
                DailyPreviewReceiver.buildAndShow(this@MainActivity)
            }
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0C0C0C),
                    surface = Color(0xFF0C0C0C),
                    primary = Color(0xFF16C60C),
                    onBackground = Color(0xFFCCCCCC),
                    onSurface = Color(0xFFCCCCCC),
                ),
                typography = com.reminder.ui.AppTypography,
            ) {
                Surface { ReminderApp(viewModel) }
            }
        }
    }
}
