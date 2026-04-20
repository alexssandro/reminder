package com.reminder.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.random.Random
import com.reminder.data.OccurrenceRow
import com.reminder.data.ReminderOverrideRow
import com.reminder.data.ReminderRow
import com.reminder.data.ScheduleKind
import com.reminder.notifications.TodayItem
import com.reminder.notifications.todayItems
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// --- Palette — cold tones (icy blue / cyan / indigo) ---
private val DarkBg = Color(0xFF0A1628)
private val DarkCard = Color(0xFF14243D)
private val DarkCardLight = Color(0xFF1B2E4A)
private val TextPrimary = Color(0xFFE8F0FA)
private val TextSecondary = Color(0xFF8FA4BF)
private val TextMuted = Color(0xFF556074)
private val AccentGradientStart = Color(0xFF22D3EE)
private val AccentGradientEnd = Color(0xFF6366F1)
private val AccentBlue = Color(0xFF38BDF8)
private val AccentBlueBright = Color(0xFF7DD3FC)
private val GreenAccent = Color(0xFF34D399)
private val GreenSurface = Color(0xFF134E4A)
private val RedAccent = Color(0xFFF472B6)
private val WarmYellow = Color(0xFFA5B4FC)

private enum class Screen { Home, Manage }

@Composable
fun ReminderApp(vm: ReminderViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    when (screen) {
        Screen.Home -> HomeScreen(vm, onOpenManage = { screen = Screen.Manage })
        Screen.Manage -> ManageScreen(vm, onBack = { screen = Screen.Home })
    }
}

@Composable
private fun HomeScreen(vm: ReminderViewModel, onOpenManage: () -> Unit) {
    val reminders by vm.reminders.collectAsState()
    val pending by vm.pendingOccurrences.collectAsState()
    val checkedToday by vm.checkedToday.collectAsState()
    val overridesToday by vm.overridesToday.collectAsState()

    var confettiTrigger by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Reminder",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Your daily check-ins",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                    IconButton(onClick = onOpenManage) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = "Manage reminders",
                            tint = TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            val today = todayItems(reminders, System.currentTimeMillis(), overridesToday)
            if (today.isNotEmpty()) {
                item { SectionHeader("Today") }
                item { TodayCard(today) }
                item { Spacer(Modifier.height(4.dp)) }
            }

            if (pending.isNotEmpty()) {
                item { SectionHeader("Due \u2014 check them off") }
                items(pending, key = { "p${it.id}" }) { occ ->
                    val reminder = reminders.firstOrNull { it.id == occ.reminderLocalId }
                    PendingRow(occ, reminder?.description ?: "Reminder") {
                        vm.check(occ)
                        confettiTrigger++
                    }
                }
            } else if (today.isEmpty() && checkedToday.isEmpty()) {
                item { HomeEmptyState(onOpenManage) }
            } else if (checkedToday.isEmpty()) {
                item { Spacer(Modifier.height(4.dp)) }
                item { HomeAllCaughtUp() }
            }

            if (checkedToday.isNotEmpty()) {
                item { Spacer(Modifier.height(4.dp)) }
                item { SectionHeader("Done today") }
                items(checkedToday, key = { "d${it.id}" }) { occ ->
                    val reminder = reminders.firstOrNull { it.id == occ.reminderLocalId }
                    DoneRow(occ, reminder?.description ?: "Reminder")
                }
            }
        }

        if (confettiTrigger > 0) {
            key(confettiTrigger) { ConfettiEffect() }
        }
    }
}

@Composable
private fun HomeAllCaughtUp() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkCard,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("\u2728", fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                "Nothing to check off right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun HomeEmptyState(onOpenManage: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkCard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("\uD83D\uDCED", fontSize = 42.sp)
            Spacer(Modifier.height(14.dp))
            Text(
                "No reminders yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Open the manage screen to add your first one.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onOpenManage) {
                Text("Open manage", color = AccentBlueBright, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ManageScreen(vm: ReminderViewModel, onBack: () -> Unit) {
    val reminders by vm.reminders.collectAsState()
    var confirmDelete by remember { mutableStateOf<ReminderRow?>(null) }
    var overrideFor by remember { mutableStateOf<ReminderRow?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 20.dp,
                bottom = 100.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(
                            "Manage reminders",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Text(
                            "Create, toggle, override, or delete",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (reminders.isEmpty()) {
                item { EmptyState() }
            } else {
                items(reminders, key = { "m${it.id}" }) { r ->
                    ManageRow(
                        r,
                        onToggle = { vm.toggleActive(r) },
                        onOverride = if (r.scheduleKind == ScheduleKind.Daily) {
                            { overrideFor = r }
                        } else null,
                        onDelete = { confirmDelete = r },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        0f to DarkBg.copy(alpha = 0f),
                        0.4f to DarkBg.copy(alpha = 0.85f),
                        1f to DarkBg,
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            GradientButton(label = "+  New reminder", onClick = { showCreate = true })
        }
    }

    if (showCreate) {
        CreateReminderDialog(
            onDismiss = { showCreate = false },
            onCreate = { description, kind, minuteOfDay, oneTimeUtc, weeklyMask ->
                vm.createReminder(description, kind, minuteOfDay, oneTimeUtc, weeklyMask)
                showCreate = false
            }
        )
    }

    overrideFor?.let { r ->
        OverrideDialog(
            reminder = r,
            overrides = vm.observeOverrides(r.id).collectAsState(initial = emptyList()).value,
            onDismiss = { overrideFor = null },
            onSave = { date, minute -> vm.setOverride(r, date, minute) },
            onRemove = { id -> vm.removeOverride(r, id) },
        )
    }

    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(r)
                    confirmDelete = null
                }) { Text("Delete", color = RedAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            title = { Text("Delete reminder?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\u201C${r.description}\u201D will be removed, along with its overrides.",
                    color = TextSecondary,
                )
            },
            containerColor = DarkCard,
            shape = RoundedCornerShape(20.dp),
        )
    }
}

@Composable
private fun ManageRow(
    r: ReminderRow,
    onToggle: () -> Unit,
    onOverride: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkCard,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScheduleIcon(kind = r.scheduleKind, unsynced = r.serverId == null)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        r.description,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (r.isActive) TextPrimary else TextMuted,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        scheduleSummary(r),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = r.isActive,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentBlue,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkCardLight,
                        uncheckedBorderColor = TextMuted.copy(alpha = 0.4f),
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = DarkCardLight)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onOverride != null) {
                    TextButton(onClick = onOverride) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = AccentBlueBright,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Override",
                            color = AccentBlueBright,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = RedAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Delete",
                        color = RedAccent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = TextMuted,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun PendingRow(occ: OccurrenceRow, description: String, onCheck: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, WarmYellow.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("\uD83D\uDD14", fontSize = 22.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Due " + formatInstant(occ.dueAtUtc),
                    style = MaterialTheme.typography.labelSmall,
                    color = WarmYellow,
                )
            }
            Spacer(Modifier.width(10.dp))
            CheckCircle(onClick = onCheck)
        }
    }
}

@Composable
private fun DoneRow(occ: OccurrenceRow, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = GreenSurface.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, GreenAccent.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GreenAccent),
                contentAlignment = Alignment.Center,
            ) {
                Text("\u2713", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = GreenAccent,
                    textDecoration = TextDecoration.LineThrough,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Checked " + formatInstant(occ.checkedAtUtc ?: occ.dueAtUtc),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun CheckCircle(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(2.dp, AccentBlueBright),
    ) {}
}

@Composable
private fun ScheduleIcon(kind: ScheduleKind, unsynced: Boolean = false) {
    val (vector, tint, label) = when (kind) {
        ScheduleKind.Daily -> Triple(Icons.Outlined.Autorenew, AccentBlueBright, "Daily")
        ScheduleKind.OneTime -> Triple(Icons.Outlined.Event, GreenAccent, "One-time")
        ScheduleKind.Weekly -> Triple(Icons.Outlined.CalendarMonth, WarmYellow, "Weekly")
    }
    Box(modifier = Modifier.size(44.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                vector,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        if (unsynced) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(DarkCard)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(WarmYellow),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = "Not synced yet",
                    tint = DarkBg,
                    modifier = Modifier.size(9.dp),
                )
            }
        }
    }
}

@Composable
private fun TodayCard(items: List<TodayItem>) {
    Column(
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.timeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.width(54.dp),
                )
                Icon(
                    when (item.kind) {
                        ScheduleKind.Daily -> Icons.Outlined.Autorenew
                        ScheduleKind.OneTime -> Icons.Outlined.Event
                        ScheduleKind.Weekly -> Icons.Outlined.CalendarMonth
                    },
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkCard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("\uD83D\uDCED", fontSize = 42.sp)
            Spacer(Modifier.height(14.dp))
            Text(
                "No reminders yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap \u201CNew reminder\u201D to add your first one.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun GradientButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(27.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(AccentGradientStart, AccentGradientEnd),
                    ),
                    shape = RoundedCornerShape(27.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
        }
    }
}

private fun scheduleSummary(r: ReminderRow): String = when (r.scheduleKind) {
    ScheduleKind.Daily -> {
        val m = r.dailyMinuteOfDay ?: 0
        "Daily at %02d:%02d".format(m / 60, m % 60)
    }
    ScheduleKind.OneTime -> r.oneTimeDueAtUtc?.let { "Once on " + formatInstant(it) } ?: "Once"
    ScheduleKind.Weekly -> {
        val m = r.dailyMinuteOfDay ?: 0
        val mask = r.weeklyDaysMask ?: 0
        val days = weekdayShortNames(mask)
        if (days.isEmpty()) "Weekly"
        else "Weekly on %s at %02d:%02d".format(days.joinToString(", "), m / 60, m % 60)
    }
}

private val WeekdayShort = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val WeekdayLetter = listOf("S", "M", "T", "W", "T", "F", "S")

private fun weekdayShortNames(mask: Int): List<String> =
    (0..6).filter { (mask shr it) and 1 == 1 }.map { WeekdayShort[it] }

private fun formatInstant(epochMillis: Long): String {
    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
    return ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

@Composable
private fun CreateReminderDialog(
    onDismiss: () -> Unit,
    onCreate: (description: String, kind: ScheduleKind, dailyMinuteOfDay: Int?, oneTimeUtc: Long?, weeklyDaysMask: Int?) -> Unit,
) {
    var description by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(ScheduleKind.Daily) }
    var hour by rememberSaveable { mutableStateOf(9) }
    var minute by rememberSaveable { mutableStateOf(0) }
    var year by rememberSaveable { mutableStateOf(LocalDate.now().year) }
    var month by rememberSaveable { mutableStateOf(LocalDate.now().monthValue) }
    var day by rememberSaveable { mutableStateOf(LocalDate.now().dayOfMonth) }
    var weeklyMask by rememberSaveable { mutableStateOf(0) }

    Dialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "New reminder",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMuted,
                    )
                }
            }

            DarkField(
                value = description,
                onChange = { description = it },
                label = "Description",
            )

            Text(
                "Frequency",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindChip(
                    label = "Daily",
                    icon = Icons.Outlined.Autorenew,
                    selected = kind == ScheduleKind.Daily,
                    onClick = { kind = ScheduleKind.Daily },
                    modifier = Modifier.weight(1f),
                )
                KindChip(
                    label = "Weekly",
                    icon = Icons.Outlined.CalendarMonth,
                    selected = kind == ScheduleKind.Weekly,
                    onClick = { kind = ScheduleKind.Weekly },
                    modifier = Modifier.weight(1f),
                )
                KindChip(
                    label = "Once",
                    icon = Icons.Outlined.Event,
                    selected = kind == ScheduleKind.OneTime,
                    onClick = { kind = ScheduleKind.OneTime },
                    modifier = Modifier.weight(1f),
                )
            }

            FieldLabel("Time")
            TimeFieldButton(hour = hour, minute = minute) { h, m -> hour = h; minute = m }

            if (kind == ScheduleKind.OneTime) {
                FieldLabel("Date")
                DateFieldButton(year = year, month = month, day = day) { y, mo, d ->
                    year = y; month = mo; day = d
                }
            }

            if (kind == ScheduleKind.Weekly) {
                FieldLabel("Days of week")
                WeekdaySelector(mask = weeklyMask, onChange = { weeklyMask = it })
            }

            Spacer(Modifier.height(4.dp))
            val canCreate = description.isNotBlank() &&
                (kind != ScheduleKind.Weekly || (weeklyMask and 0x7F) != 0)
            GradientButton(
                label = "Create reminder",
                enabled = canCreate,
                onClick = {
                    when (kind) {
                        ScheduleKind.Daily -> onCreate(description.trim(), kind, hour * 60 + minute, null, null)
                        ScheduleKind.OneTime -> {
                            val ldt = LocalDateTime.of(
                                LocalDate.of(year, month, day),
                                LocalTime.of(hour, minute),
                            )
                            val millis = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            onCreate(description.trim(), kind, null, millis, null)
                        }
                        ScheduleKind.Weekly -> onCreate(
                            description.trim(), kind, hour * 60 + minute, null, weeklyMask and 0x7F,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun OverrideDialog(
    reminder: ReminderRow,
    overrides: List<ReminderOverrideRow>,
    onDismiss: () -> Unit,
    onSave: (localDate: String, minuteOfDay: Int) -> Unit,
    onRemove: (overrideId: Long) -> Unit,
) {
    val todayLocal = LocalDate.now()
    var year by rememberSaveable { mutableStateOf(todayLocal.year) }
    var month by rememberSaveable { mutableStateOf(todayLocal.monthValue) }
    var day by rememberSaveable { mutableStateOf(todayLocal.dayOfMonth) }
    var hour by rememberSaveable {
        mutableStateOf((reminder.dailyMinuteOfDay ?: 0) / 60)
    }
    var minute by rememberSaveable {
        mutableStateOf((reminder.dailyMinuteOfDay ?: 0) % 60)
    }

    Dialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Override for a day",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    val base = reminder.dailyMinuteOfDay ?: 0
                    Text(
                        "${reminder.description} \u00B7 normally %02d:%02d".format(base / 60, base % 60),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMuted,
                    )
                }
            }

            FieldLabel("Date")
            DateFieldButton(year = year, month = month, day = day) { y, mo, d ->
                year = y; month = mo; day = d
            }

            FieldLabel("New time")
            TimeFieldButton(hour = hour, minute = minute) { h, m -> hour = h; minute = m }

            GradientButton(
                label = "Save override",
                onClick = {
                    val date = runCatching { LocalDate.of(year, month, day) }.getOrNull()
                    if (date != null) {
                        onSave(date.toString(), hour * 60 + minute)
                    }
                },
            )

            if (overrides.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "EXISTING",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted,
                )
                overrides.forEach { o ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = DarkCard,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    o.localDate,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                )
                                Text(
                                    "at %02d:%02d".format(o.minuteOfDay / 60, o.minuteOfDay % 60),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentBlueBright,
                                )
                            }
                            IconButton(onClick = { onRemove(o.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove override",
                                    tint = TextMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkBg,
            border = BorderStroke(1.dp, DarkCardLight),
        ) {
            Box(modifier = Modifier.padding(20.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun KindChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) AccentBlue.copy(alpha = 0.18f) else DarkCard,
        border = if (selected)
            BorderStroke(1.5.dp, AccentBlueBright)
        else
            BorderStroke(1.dp, DarkCardLight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) AccentBlueBright else TextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                label,
                color = if (selected) AccentBlueBright else TextSecondary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DarkField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AccentBlueBright,
            focusedBorderColor = AccentBlueBright,
            unfocusedBorderColor = DarkCardLight,
            focusedLabelColor = AccentBlueBright,
            unfocusedLabelColor = TextMuted,
            focusedContainerColor = DarkCard,
            unfocusedContainerColor = DarkCard,
        ),
    )
}

@Composable
private fun WeekdaySelector(mask: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).forEach { i ->
            val selected = (mask shr i) and 1 == 1
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onChange(mask xor (1 shl i)) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) AccentBlue.copy(alpha = 0.22f) else DarkCard,
                border = BorderStroke(
                    if (selected) 1.5.dp else 1.dp,
                    if (selected) AccentBlueBright else DarkCardLight,
                ),
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        WeekdayLetter[i],
                        color = if (selected) AccentBlueBright else TextSecondary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = TextMuted,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFieldButton(
    year: Int,
    month: Int,
    day: Int,
    onPicked: (year: Int, month: Int, day: Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val display = "%04d-%02d-%02d".format(year, month, day)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true },
        shape = RoundedCornerShape(14.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, DarkCardLight),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Event,
                contentDescription = null,
                tint = AccentBlueBright,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(display, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
    }

    if (open) {
        val initial = LocalDate.of(year, month, day)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val ld = LocalDate.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC)
                        onPicked(ld.year, ld.monthValue, ld.dayOfMonth)
                    }
                    open = false
                }) { Text("OK", color = AccentBlueBright) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel", color = TextSecondary) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeFieldButton(
    hour: Int,
    minute: Int,
    onPicked: (hour: Int, minute: Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val display = "%02d:%02d".format(hour, minute)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true },
        shape = RoundedCornerShape(14.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, DarkCardLight),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = AccentBlueBright,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(display, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
    }

    if (open) {
        val state = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true,
        )
        Dialog(onDismiss = { open = false }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { open = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        onPicked(state.hour, state.minute)
                        open = false
                    }) {
                        Text("OK", color = AccentBlueBright)
                    }
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val x: Float,
    val delay: Float,
    val duration: Float,
    val spin: Float,
    val angle: Float,
    val size: Float,
    val color: Color,
    val wobble: Float,
    val wobbleSpeed: Float,
)

@Composable
private fun ConfettiEffect() {
    val particles = remember {
        val colors = listOf(
            Color(0xFF4ADE80), Color(0xFF34D399), Color(0xFF22D3EE),
            Color(0xFFFCD34D), Color(0xFFF472B6), Color(0xFFA78BFA),
            Color(0xFF60A5FA), Color(0xFF818CF8), Color(0xFFFBBF24),
        )
        List(80) {
            val duration = 0.55f + Random.nextFloat() * 0.35f
            val delay = Random.nextFloat() * (1f - duration)
            ConfettiParticle(
                x = Random.nextFloat(),
                delay = delay,
                duration = duration,
                spin = 1f + Random.nextFloat() * 2f,
                angle = Random.nextFloat() * 360f,
                size = 4f + Random.nextFloat() * 10f,
                color = colors[Random.nextInt(colors.size)],
                wobble = 20f + Random.nextFloat() * 40f,
                wobbleSpeed = 1f + Random.nextFloat() * 3f,
            )
        }
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 3500, easing = LinearEasing))
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = progress.value

        particles.forEach { p ->
            val local = ((t - p.delay) / p.duration).coerceIn(0f, 1f)
            val travel = h + p.size * 2 + 120f
            val y = -p.size - 60f + travel * local
            val wobbleOffset = sin(local * p.wobbleSpeed * 6.28f) * p.wobble
            val px = p.x * w + wobbleOffset
            val rotation = p.angle + local * 360f * p.spin

            if (local > 0f && y in -p.size..(h + p.size)) {
                rotate(rotation, pivot = Offset(px, y)) {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(px - p.size / 2, y - p.size / 2),
                        size = androidx.compose.ui.geometry.Size(p.size, p.size * 0.6f),
                    )
                }
            }
        }
    }
}
