package com.reminder.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.random.Random
import com.reminder.BuildConfig
import com.reminder.data.ChecklistItemRow
import com.reminder.data.OccurrenceRow
import com.reminder.data.ReminderOverrideRow
import com.reminder.data.ReminderRow
import com.reminder.data.ScheduleKind
import com.reminder.data.WishlistItemRow
import com.reminder.notifications.DailyPreviewReceiver
import com.reminder.notifications.TodayItem
import com.reminder.notifications.monthlyAvailableOn
import com.reminder.notifications.todayItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// --- Palette — Windows Terminal "Campbell" scheme: terminal look ---
private val DarkBg = Color(0xFF0C0C0C)
private val DarkCard = Color(0xFF161616)
private val DarkCardLight = Color(0xFF222222)
private val TextPrimary = Color(0xFFCCCCCC)
private val TextSecondary = Color(0xFF9E9E9E)
private val TextMuted = Color(0xFF767676)
private val AccentGradientStart = Color(0xFF16C60C)
private val AccentGradientEnd = Color(0xFF13A10E)
private val AccentBlue = Color(0xFF3A96DD)
private val AccentBlueBright = Color(0xFF61D6D6)
private val GreenAccent = Color(0xFF16C60C)
private val GreenSurface = Color(0xFF0D2E0B)
private val RedAccent = Color(0xFFE74856)
private val WarmYellow = Color(0xFFF9F1A5)
private val VioletAccent = Color(0xFF9E7BFF)
private val TealAccent = Color(0xFF3A96DD)

private enum class Screen { Home, Manage, Wishlist }

@Composable
fun ReminderApp(vm: ReminderViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    when (screen) {
        Screen.Home -> HomeScreen(
            vm,
            onOpenManage = { screen = Screen.Manage },
            onOpenWishlist = { screen = Screen.Wishlist },
        )
        Screen.Manage -> ManageScreen(vm, onBack = { screen = Screen.Home })
        Screen.Wishlist -> WishlistScreen(vm, onBack = { screen = Screen.Home })
    }
}

@Composable
private fun HomeScreen(
    vm: ReminderViewModel,
    onOpenManage: () -> Unit,
    onOpenWishlist: () -> Unit,
) {
    val reminders by vm.reminders.collectAsState()
    val pending by vm.pendingOccurrences.collectAsState()
    val checkedToday by vm.checkedToday.collectAsState()
    val everCheckedIds by vm.everCheckedReminderIds.collectAsState()
    val overridesToday by vm.overridesToday.collectAsState()
    val checklistItems by vm.checklistItems.collectAsState()
    val checklistChecksToday by vm.checklistChecksToday.collectAsState()

    val checklistByReminder = checklistItems.groupBy { it.reminderLocalId }
    val checkedItemIds = checklistChecksToday.map { it.checklistItemLocalId }.toSet()

    var confettiTrigger by remember { mutableStateOf(0) }

    // A pending occurrence whose reminder has been edited (time, kind, weekly mask, or
    // toggled inactive) is stale — hide it so the home screen reflects the edit immediately
    // instead of being pinned to the old fire row.
    val livePending = pending.filter { occ ->
        val r = reminders.firstOrNull { it.id == occ.reminderLocalId } ?: return@filter true
        val occDate = LocalDate.ofInstant(Instant.ofEpochMilli(occ.dueAtUtc), ZoneId.systemDefault())
        val override = overridesToday.firstOrNull {
            it.reminderLocalId == r.id && it.localDate == occDate.toString()
        }?.minuteOfDay
        com.reminder.notifications.reminderFiresAt(r, occ.dueAtUtc, override)
    }

    val nowMs = System.currentTimeMillis()
    val touched = (livePending.map { it.reminderLocalId } + checkedToday.map { it.reminderLocalId }).toSet()

    val (overdueToday, laterToday) = todayItems(reminders, nowMs, overridesToday)
        .filter { it.reminderLocalId !in touched }
        .partition { dueAtUtcForToday(it.minuteOfDay) <= nowMs }

    // Anytime reminders are "done forever" once checked — they never reset, so they drop off
    // permanently (filtered by everCheckedIds), unlike Daily/Monthly which reset each period.
    val anytimeItems = reminders.filter {
        it.isActive && !it.pendingDelete &&
            it.scheduleKind == ScheduleKind.Anytime &&
            it.id !in touched &&
            it.id !in everCheckedIds
    }

    // Monthly reminders surface only on their chosen day of the month (clamped in short months).
    val todayDate = LocalDate.now()
    val monthlyItems = reminders.filter {
        it.isActive && !it.pendingDelete &&
            it.scheduleKind == ScheduleKind.Monthly &&
            it.monthlyDayOfMonth?.let { d -> monthlyAvailableOn(todayDate, d) } == true &&
            it.id !in touched
    }

    val pendingCount = livePending.size + overdueToday.size + laterToday.size +
        monthlyItems.size + anytimeItems.size
    var showChecked by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "reminder:~$ ",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = GreenAccent,
                            )
                            BlinkingCursor()
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (pendingCount > 0) "// $pendingCount pending · ${checkedToday.size} done"
                            else "// all done · ${checkedToday.size} checked today",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (pendingCount > 0) WarmYellow else GreenAccent,
                        )
                        val total = pendingCount + checkedToday.size
                        if (total > 0) {
                            Spacer(Modifier.height(6.dp))
                            val barWidth = 14
                            val filled = checkedToday.size * barWidth / total
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = TextMuted)) { append("[") }
                                    withStyle(SpanStyle(color = GreenAccent)) { append("█".repeat(filled)) }
                                    withStyle(SpanStyle(color = TextMuted.copy(alpha = 0.35f))) {
                                        append("░".repeat(barWidth - filled))
                                    }
                                    withStyle(SpanStyle(color = TextMuted)) {
                                        append("] ${checkedToday.size}/$total")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    IconButton(onClick = onOpenWishlist) {
                        Icon(
                            Icons.Outlined.Redeem,
                            contentDescription = "Wishlist",
                            tint = TextSecondary,
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

            val needsAttention = livePending.isNotEmpty() || overdueToday.isNotEmpty()
            if (needsAttention) {
                item { SectionHeader("Needs attention") }
                items(livePending, key = { "p${it.id}" }) { occ ->
                    val reminder = reminders.firstOrNull { it.id == occ.reminderLocalId }
                    PendingRow(
                        occ,
                        reminder?.description ?: "Reminder",
                        checklistByReminder[occ.reminderLocalId].orEmpty(),
                        checkedItemIds,
                        vm::toggleChecklistItem,
                    ) {
                        vm.check(occ)
                        confettiTrigger++
                    }
                }
                items(overdueToday, key = { "o${it.reminderLocalId}" }) { item ->
                    val reminder = reminders.firstOrNull { it.id == item.reminderLocalId }
                    OverdueRow(
                        item,
                        checklistByReminder[item.reminderLocalId].orEmpty(),
                        checkedItemIds,
                        vm::toggleChecklistItem,
                    ) {
                        if (reminder != null) {
                            vm.checkAhead(reminder, dueAtUtcForToday(item.minuteOfDay))
                            confettiTrigger++
                        }
                    }
                }
            }

            if (laterToday.isNotEmpty()) {
                if (needsAttention) item { Spacer(Modifier.height(4.dp)) }
                item { SectionHeader("Later today") }
                items(laterToday, key = { "l${it.reminderLocalId}" }) { item ->
                    val reminder = reminders.firstOrNull { it.id == item.reminderLocalId }
                    UpcomingRow(
                        item,
                        checklistByReminder[item.reminderLocalId].orEmpty(),
                        checkedItemIds,
                        vm::toggleChecklistItem,
                    ) {
                        if (reminder != null) {
                            vm.checkAhead(reminder, dueAtUtcForToday(item.minuteOfDay))
                            confettiTrigger++
                        }
                    }
                }
            }

            if (monthlyItems.isNotEmpty()) {
                if (needsAttention || laterToday.isNotEmpty()) item { Spacer(Modifier.height(4.dp)) }
                item { SectionHeader("Today's monthly") }
                items(monthlyItems, key = { "mo${it.id}" }) { reminder ->
                    UntimedRow(
                        reminder,
                        label = "Monthly · day ${reminder.monthlyDayOfMonth}",
                        tint = TealAccent,
                        checklist = checklistByReminder[reminder.id].orEmpty(),
                        checkedItemIds = checkedItemIds,
                        onToggleChecklistItem = vm::toggleChecklistItem,
                    ) {
                        vm.checkAhead(reminder, nowMs)
                        confettiTrigger++
                    }
                }
            }

            if (anytimeItems.isNotEmpty()) {
                if (needsAttention || laterToday.isNotEmpty() || monthlyItems.isNotEmpty()) {
                    item { Spacer(Modifier.height(4.dp)) }
                }
                item { SectionHeader("Anytime") }
                items(anytimeItems, key = { "a${it.id}" }) { reminder ->
                    UntimedRow(
                        reminder,
                        label = "Anytime",
                        tint = VioletAccent,
                        checklist = checklistByReminder[reminder.id].orEmpty(),
                        checkedItemIds = checkedItemIds,
                        onToggleChecklistItem = vm::toggleChecklistItem,
                    ) {
                        vm.checkAhead(reminder, nowMs)
                        confettiTrigger++
                    }
                }
            }

            if (pendingCount == 0) {
                if (reminders.isEmpty()) {
                    item { HomeEmptyState(onOpenManage) }
                } else {
                    item { HomeAllCaughtUp() }
                }
            }

            // Checked items are collapsed by default — pending work owns the screen.
            if (checkedToday.isNotEmpty()) {
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .clickable { showChecked = !showChecked }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (if (showChecked) "[-]" else "[+]") + " checked (${checkedToday.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted,
                        )
                    }
                }
                if (showChecked) {
                    items(checkedToday, key = { "d${it.id}" }) { occ ->
                        val reminder = reminders.firstOrNull { it.id == occ.reminderLocalId }
                        DoneRow(
                            occ,
                            reminder?.description ?: "Reminder",
                            checklistByReminder[occ.reminderLocalId].orEmpty(),
                            checkedItemIds,
                            vm::toggleChecklistItem,
                        ) { vm.uncheck(occ) }
                    }
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
        shape = RoundedCornerShape(2.dp),
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
        shape = RoundedCornerShape(2.dp),
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
    val checklistItems by vm.checklistItems.collectAsState()
    var confirmDelete by remember { mutableStateOf<ReminderRow?>(null) }
    var overrideFor by remember { mutableStateOf<ReminderRow?>(null) }
    var editFor by remember { mutableStateOf<ReminderRow?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .statusBarsPadding(),
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
                            "reminder:~/manage$",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = GreenAccent,
                        )
                        Text(
                            "// create, toggle, override, delete",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
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
                        onEdit = { editFor = r },
                        onOverride = if (r.scheduleKind == ScheduleKind.Daily) {
                            { overrideFor = r }
                        } else null,
                        onDelete = { confirmDelete = r },
                    )
                }
            }

            // Debug-only: fire the daily preview on demand to verify notification behavior
            // (including the skip-when-everything-is-checked path) without waiting for a slot.
            if (BuildConfig.DEBUG) {
                item {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    TextButton(onClick = {
                        scope.launch {
                            val shown = withContext(Dispatchers.IO) {
                                DailyPreviewReceiver.buildAndShow(context)
                            }
                            Toast.makeText(
                                context,
                                if (shown) "Test notification sent"
                                else "Skipped — everything is already checked off",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }) {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = TextSecondary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Send test notification", color = TextSecondary)
                    }
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

    if (showCreate || editFor != null) {
        val target = editFor
        val initialChecklist = target?.let { t ->
            checklistItems.filter { it.reminderLocalId == t.id }
                .sortedBy { it.position }
                .map { it.text }
        } ?: emptyList()
        ReminderEditorDialog(
            initial = target,
            initialChecklist = initialChecklist,
            onDismiss = { showCreate = false; editFor = null },
            onSave = { description, kind, minuteOfDay, oneTimeUtc, weeklyMask, monthDay, checklist ->
                if (target != null) {
                    vm.updateSchedule(target, description, kind, minuteOfDay, oneTimeUtc, weeklyMask, monthDay, checklist)
                } else {
                    vm.createReminder(description, kind, minuteOfDay, oneTimeUtc, weeklyMask, monthDay, checklist)
                }
                showCreate = false
                editFor = null
            },
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
            shape = RoundedCornerShape(2.dp),
        )
    }
}

@Composable
private fun ManageRow(
    r: ReminderRow,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onOverride: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(2.dp),
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
                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = AccentBlueBright,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Edit",
                        color = AccentBlueBright,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (onOverride != null) {
                    TextButton(onClick = onOverride) {
                        Icon(
                            Icons.Outlined.EditCalendar,
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
        "> " + text.lowercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = GreenAccent,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/**
 * Terminal-style status marker leading every Home row: "[!]" needs attention,
 * "[ ]" still open today, "[x]" done. Mono font keeps them column-aligned.
 */
@Composable
private fun StatusGlyph(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

/** Solid block cursor blinking at terminal cadence next to the prompt header. */
@Composable
private fun BlinkingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )
    Text(
        "█",
        style = MaterialTheme.typography.headlineMedium,
        color = GreenAccent.copy(alpha = alpha),
    )
}

/** How long the item must be held before it checks off. */
private const val HOLD_TO_CHECK_MILLIS = 3000

/**
 * Wraps [content] in a hold-to-act area: holding for [HOLD_TO_CHECK_MILLIS] runs [onComplete]
 * (with a haptic tick) while a [fillColor] bar grows left→right behind the content to show
 * progress; releasing early retracts it. A quick tap (released almost immediately) runs [onTap]
 * if provided. Used for the small checklist sub-item rows.
 */
@Composable
private fun HoldToAct(
    onComplete: () -> Unit,
    fillColor: Color,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(2.dp)
    var holding by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val completed = remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(holding) {
        if (holding) {
            completed.value = false
            progress.snapTo(0f)
            progress.animateTo(1f, tween(HOLD_TO_CHECK_MILLIS, easing = LinearEasing))
            completed.value = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onComplete()
            holding = false
        } else {
            progress.animateTo(0f, tween(180))
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    holding = true
                    tryAwaitRelease()
                    val heldFraction = progress.value
                    val done = completed.value
                    holding = false
                    if (!done && heldFraction < 0.08f) onTap?.invoke()
                })
            },
    ) {
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                    .background(fillColor.copy(alpha = 0.22f))
            )
        }
        content()
    }
}

/**
 * A reminder card you check off by *holding* its main row for [HOLD_TO_CHECK_MILLIS] (no
 * checkbox). While held, a tinted bar fills the card left→right to show progress; releasing
 * early retracts it. The optional [checklist] sits below the hold zone so its per-day taps
 * stay independent of the hold gesture.
 */
@Composable
private fun HoldToCheckCard(
    accent: Color,
    surfaceColor: Color,
    border: BorderStroke,
    onCheck: () -> Unit,
    checklist: @Composable () -> Unit = {},
    body: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(2.dp)
    var holding by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(holding) {
        if (holding) {
            progress.snapTo(0f)
            // animateTo only returns normally if the hold runs the full duration; an early
            // release flips `holding`, cancelling this coroutine before the check fires.
            progress.animateTo(1f, tween(HOLD_TO_CHECK_MILLIS, easing = LinearEasing))
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onCheck()
            holding = false
        } else {
            progress.animateTo(0f, tween(180))
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = surfaceColor,
        border = border,
    ) {
        Box {
            // The growing "checking" fill, clipped to the card and sitting behind the content.
            Box(Modifier.matchParentSize().clip(shape)) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                        .background(accent.copy(alpha = 0.22f))
                )
            }
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(onPress = {
                                    holding = true
                                    tryAwaitRelease()
                                    holding = false
                                })
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        content = body,
                    )
                    checklist()
                }
            }
        }
    }
}

@Composable
private fun PendingRow(
    occ: OccurrenceRow,
    description: String,
    checklist: List<ChecklistItemRow>,
    checkedItemIds: Set<Long>,
    onToggleChecklistItem: (itemId: Long, checked: Boolean) -> Unit,
    onCheck: () -> Unit,
) {
    HoldToCheckCard(
        accent = WarmYellow,
        surfaceColor = DarkCardLight,
        border = BorderStroke(1.5.dp, WarmYellow.copy(alpha = 0.8f)),
        onCheck = onCheck,
        checklist = { ChecklistBlock(checklist, checkedItemIds, onToggleChecklistItem) },
    ) {
        StatusGlyph("[!]", WarmYellow)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Due " + formatInstant(occ.dueAtUtc),
                style = MaterialTheme.typography.labelSmall,
                color = WarmYellow,
            )
        }
    }
}

@Composable
private fun DoneRow(
    occ: OccurrenceRow,
    description: String,
    checklist: List<ChecklistItemRow>,
    checkedItemIds: Set<Long>,
    onToggleChecklistItem: (itemId: Long, checked: Boolean) -> Unit,
    onUncheck: () -> Unit,
) {
    // Deliberately quiet: checked items recede so unchecked work owns the screen.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(2.dp),
        color = DarkBg,
        border = BorderStroke(1.dp, DarkCardLight),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusGlyph("[x]", GreenAccent.copy(alpha = 0.45f))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textDecoration = TextDecoration.LineThrough,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Checked " + formatInstant(occ.checkedAtUtc ?: occ.dueAtUtc),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onUncheck) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Undo",
                        color = TextMuted,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            ChecklistBlock(checklist, checkedItemIds, onToggleChecklistItem, muted = true)
        }
    }
}

@Composable
private fun OverdueRow(
    item: TodayItem,
    checklist: List<ChecklistItemRow>,
    checkedItemIds: Set<Long>,
    onToggleChecklistItem: (itemId: Long, checked: Boolean) -> Unit,
    onCheck: () -> Unit,
) {
    HoldToCheckCard(
        accent = WarmYellow,
        surfaceColor = DarkCardLight,
        border = BorderStroke(1.5.dp, WarmYellow.copy(alpha = 0.8f)),
        onCheck = onCheck,
        checklist = { ChecklistBlock(checklist, checkedItemIds, onToggleChecklistItem) },
    ) {
        StatusGlyph("[!]", WarmYellow)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Was due " + item.timeLabel + " today",
                style = MaterialTheme.typography.labelSmall,
                color = WarmYellow,
            )
        }
    }
}

@Composable
private fun ScheduleIcon(kind: ScheduleKind, unsynced: Boolean = false) {
    val (vector, tint, label) = when (kind) {
        ScheduleKind.Daily -> Triple(Icons.Outlined.Autorenew, AccentBlueBright, "Daily")
        ScheduleKind.OneTime -> Triple(Icons.Outlined.Event, GreenAccent, "One-time")
        ScheduleKind.Weekly -> Triple(Icons.Outlined.CalendarMonth, WarmYellow, "Weekly")
        ScheduleKind.Anytime -> Triple(Icons.Outlined.AllInclusive, VioletAccent, "Anytime")
        ScheduleKind.Monthly -> Triple(Icons.Outlined.EventRepeat, TealAccent, "Monthly")
    }
    Box(modifier = Modifier.size(44.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(2.dp))
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
private fun UpcomingRow(
    item: TodayItem,
    checklist: List<ChecklistItemRow>,
    checkedItemIds: Set<Long>,
    onToggleChecklistItem: (itemId: Long, checked: Boolean) -> Unit,
    onCheck: () -> Unit,
) {
    HoldToCheckCard(
        accent = AccentBlueBright,
        surfaceColor = DarkCard,
        border = BorderStroke(1.dp, DarkCardLight),
        onCheck = onCheck,
        checklist = { ChecklistBlock(checklist, checkedItemIds, onToggleChecklistItem) },
    ) {
        StatusGlyph("[ ]", AccentBlueBright)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                kindLabel(item.kind),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            item.timeLabel,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = AccentBlueBright,
        )
    }
}

/** Row for reminders with no time-of-day (Anytime, Monthly): just a label and a check. */
@Composable
private fun UntimedRow(
    reminder: ReminderRow,
    label: String,
    tint: Color,
    checklist: List<ChecklistItemRow>,
    checkedItemIds: Set<Long>,
    onToggleChecklistItem: (itemId: Long, checked: Boolean) -> Unit,
    onCheck: () -> Unit,
) {
    HoldToCheckCard(
        accent = tint,
        surfaceColor = DarkCard,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        onCheck = onCheck,
        checklist = { ChecklistBlock(checklist, checkedItemIds, onToggleChecklistItem) },
    ) {
        StatusGlyph("[ ]", tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                reminder.description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
    }
}

/** Inline, per-day checkable sub-items shown under a reminder on Home. Independent of the parent check. */
@Composable
private fun ChecklistBlock(
    items: List<ChecklistItemRow>,
    checkedItemIds: Set<Long>,
    onToggle: (itemId: Long, checked: Boolean) -> Unit,
    muted: Boolean = false,
) {
    if (items.isEmpty()) return
    Column(
        modifier = Modifier.padding(start = 36.dp, top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val checked = item.id in checkedItemIds
            val checkColor = if (muted) GreenAccent.copy(alpha = 0.35f) else GreenAccent
            // Hold to toggle (check or uncheck), matching the parent reminder's hold-to-check.
            HoldToAct(
                onComplete = { onToggle(item.id, !checked) },
                fillColor = GreenAccent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .then(
                                if (checked) Modifier.background(checkColor)
                                else Modifier.border(1.5.dp, TextMuted, RoundedCornerShape(2.dp))
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (checked) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (checked) TextMuted else TextSecondary,
                        textDecoration = if (checked) TextDecoration.LineThrough else null,
                    )
                }
            }
        }
    }
}

private fun kindLabel(kind: ScheduleKind): String = when (kind) {
    ScheduleKind.Daily -> "daily"
    ScheduleKind.OneTime -> "once"
    ScheduleKind.Weekly -> "weekly"
    ScheduleKind.Anytime -> "anytime"
    ScheduleKind.Monthly -> "monthly"
}

private fun dueAtUtcForToday(minuteOfDay: Int): Long =
    LocalDate.now()
        .atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

@Composable
private fun EmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(2.dp),
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
        shape = RoundedCornerShape(2.dp),
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
                    shape = RoundedCornerShape(2.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = Color.Black,
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
    ScheduleKind.Anytime -> "Anytime · no due date"
    ScheduleKind.Monthly -> "Monthly on day ${r.monthlyDayOfMonth ?: 1}"
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
private fun ReminderEditorDialog(
    initial: ReminderRow?,
    initialChecklist: List<String>,
    onDismiss: () -> Unit,
    onSave: (description: String, kind: ScheduleKind, dailyMinuteOfDay: Int?, oneTimeUtc: Long?, weeklyDaysMask: Int?, monthlyDayOfMonth: Int?, checklist: List<String>) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val initialOneTimeLdt = remember(initial?.id) {
        initial?.oneTimeDueAtUtc?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
    }
    val initialMinuteOfDay = remember(initial?.id) {
        initial?.dailyMinuteOfDay
            ?: initialOneTimeLdt?.let { it.hour * 60 + it.minute }
            ?: (9 * 60)
    }
    val today = remember { LocalDate.now() }

    var description by rememberSaveable(initial?.id) { mutableStateOf(initial?.description ?: "") }
    var kind by rememberSaveable(initial?.id) { mutableStateOf(initial?.scheduleKind ?: ScheduleKind.Daily) }
    var hour by rememberSaveable(initial?.id) { mutableStateOf(initialMinuteOfDay / 60) }
    var minute by rememberSaveable(initial?.id) { mutableStateOf(initialMinuteOfDay % 60) }
    var year by rememberSaveable(initial?.id) { mutableStateOf(initialOneTimeLdt?.year ?: today.year) }
    var month by rememberSaveable(initial?.id) { mutableStateOf(initialOneTimeLdt?.monthValue ?: today.monthValue) }
    var day by rememberSaveable(initial?.id) { mutableStateOf(initialOneTimeLdt?.dayOfMonth ?: today.dayOfMonth) }
    var weeklyMask by rememberSaveable(initial?.id) { mutableStateOf(initial?.weeklyDaysMask ?: 0) }
    var monthDayText by rememberSaveable(initial?.id) {
        mutableStateOf((initial?.monthlyDayOfMonth ?: today.dayOfMonth).toString())
    }
    val checklistDraft = remember(initial?.id) {
        mutableStateListOf<String>().apply { addAll(initialChecklist) }
    }

    val isEdit = initial != null
    val title = if (isEdit) "Edit reminder" else "New reminder"
    val actionLabel = if (isEdit) "Save changes" else "Create reminder"

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
                    title,
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindChip(
                        label = "Once",
                        icon = Icons.Outlined.Event,
                        selected = kind == ScheduleKind.OneTime,
                        onClick = { kind = ScheduleKind.OneTime },
                        modifier = Modifier.weight(1f),
                    )
                    KindChip(
                        label = "Monthly",
                        icon = Icons.Outlined.EventRepeat,
                        selected = kind == ScheduleKind.Monthly,
                        onClick = { kind = ScheduleKind.Monthly },
                        modifier = Modifier.weight(1f),
                    )
                }
                KindChip(
                    label = "Anytime",
                    icon = Icons.Outlined.AllInclusive,
                    selected = kind == ScheduleKind.Anytime,
                    onClick = { kind = ScheduleKind.Anytime },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Anytime and Monthly reminders have no due time, so they need no time input.
            if (kind != ScheduleKind.Anytime && kind != ScheduleKind.Monthly) {
                FieldLabel("Time")
                TimeFieldButton(hour = hour, minute = minute) { h, m -> hour = h; minute = m }
            }

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

            if (kind == ScheduleKind.Monthly) {
                FieldLabel("Day of month (1–31)")
                DarkField(
                    value = monthDayText,
                    onChange = { text -> monthDayText = text.filter { it.isDigit() }.take(2) },
                    label = "Day",
                )
                Text(
                    "Available to check off on that day each month. Months with fewer days use their last day. No alarm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }

            if (kind == ScheduleKind.Anytime) {
                Text(
                    "Always available to check off. No alarm or due time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }

            ChecklistEditor(items = checklistDraft)

            Spacer(Modifier.height(4.dp))
            val monthDay = monthDayText.toIntOrNull()
            val canSave = description.isNotBlank() &&
                (kind != ScheduleKind.Weekly || (weeklyMask and 0x7F) != 0) &&
                (kind != ScheduleKind.Monthly || (monthDay != null && monthDay in 1..31))
            GradientButton(
                label = actionLabel,
                enabled = canSave,
                onClick = {
                    val checklist = checklistDraft.toList()
                    when (kind) {
                        ScheduleKind.Daily -> onSave(description.trim(), kind, hour * 60 + minute, null, null, null, checklist)
                        ScheduleKind.OneTime -> {
                            val ldt = LocalDateTime.of(
                                LocalDate.of(year, month, day),
                                LocalTime.of(hour, minute),
                            )
                            val millis = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            onSave(description.trim(), kind, null, millis, null, null, checklist)
                        }
                        ScheduleKind.Weekly -> onSave(
                            description.trim(), kind, hour * 60 + minute, null, weeklyMask and 0x7F, null, checklist,
                        )
                        // No schedule fields — always available to check off.
                        ScheduleKind.Anytime -> onSave(description.trim(), kind, null, null, null, null, checklist)
                        ScheduleKind.Monthly -> onSave(description.trim(), kind, null, null, null, monthDay, checklist)
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
                        shape = RoundedCornerShape(2.dp),
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
            shape = RoundedCornerShape(2.dp),
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
        shape = RoundedCornerShape(2.dp),
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
private fun DarkField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(2.dp),
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
private fun ChecklistEditor(items: MutableList<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Checklist (optional)")
        items.forEachIndexed { index, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { items[index] = it },
                    placeholder = { Text("Sub-item", color = TextMuted) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(2.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentBlueBright,
                        focusedBorderColor = AccentBlueBright,
                        unfocusedBorderColor = DarkCardLight,
                        focusedContainerColor = DarkCard,
                        unfocusedContainerColor = DarkCard,
                    ),
                )
                IconButton(onClick = { items.removeAt(index) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove sub-item",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        TextButton(onClick = { items.add("") }) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = AccentBlueBright,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Add sub-item", color = AccentBlueBright, fontWeight = FontWeight.SemiBold)
        }
    }
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
                shape = RoundedCornerShape(2.dp),
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
        shape = RoundedCornerShape(2.dp),
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
        shape = RoundedCornerShape(2.dp),
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

// --- Wishlist ---------------------------------------------------------------

@Composable
private fun WishlistScreen(vm: ReminderViewModel, onBack: () -> Unit) {
    val items by vm.wishlist.collectAsState()
    var editing by remember { mutableStateOf<WishlistItemRow?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<WishlistItemRow?>(null) }
    var showBought by rememberSaveable { mutableStateOf(false) }

    // Wished-for items stay in the draggable list; bought ones recede into a collapsed
    // section below (most-recently bought first), mirroring Home's checked-off items.
    val pending = items.filter { it.boughtAtUtc == null }
    val bought = items.filter { it.boughtAtUtc != null }.sortedByDescending { it.boughtAtUtc }

    val listState = rememberLazyListState()
    // Local mirror of the pending list so drag reordering is instant; we resync whenever a
    // fresh list arrives from Room (and never mid-drag, to avoid snapping back).
    val view = remember { mutableStateListOf<WishlistItemRow>() }
    val dragState = rememberWishlistDragState(listState) { from, to ->
        if (from in view.indices && to in view.indices) view.add(to, view.removeAt(from))
    }
    LaunchedEffect(pending) {
        if (dragState.draggingItemIndex == null) {
            view.clear()
            view.addAll(pending)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = TextSecondary,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "wishlist:~$",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = GreenAccent,
                    )
                    Text(
                        when {
                            items.isEmpty() -> "// nothing yet"
                            pending.isEmpty() -> "// all bought · ${bought.size} done"
                            else -> "// ${pending.size} item${if (pending.size == 1) "" else "s"} · long-press to reorder"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add product", tint = AccentBlueBright)
                }
            }

            if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.Redeem,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No products yet",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap + to add your first wish.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Pending rows occupy LazyColumn indices 0..view.size-1, so they line up
                    // with `view` for the drag-reorder math; the bought section trails after.
                    itemsIndexed(view, key = { _, it -> it.id }) { index, item ->
                        val dragging = index == dragState.draggingItemIndex
                        WishlistRow(
                            item = item,
                            dragging = dragging,
                            rank = index + 1,
                            modifier = if (dragging) {
                                Modifier.graphicsLayer {
                                    translationY = dragState.draggingItemTranslationY
                                    shadowElevation = 12f
                                }
                            } else {
                                Modifier.animateItem()
                            },
                            // Reorder now starts from the handle only, so the row body is free
                            // for the hold-to-buy gesture. The index is resolved at drag start.
                            dragHandle = Modifier.pointerInput(item.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragState.onDragStart(view.indexOfFirst { it.id == item.id })
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragState.onDrag(amount.y)
                                    },
                                    onDragEnd = {
                                        dragState.onDragEnd()
                                        vm.reorderWishlist(view.map { it.id })
                                    },
                                    onDragCancel = { dragState.onDragEnd() },
                                )
                            },
                            onClick = { editing = item },
                            onMarkBought = { vm.toggleWishlistBought(item) },
                            onDelete = { confirmDelete = item },
                        )
                    }

                    if (bought.isNotEmpty()) {
                        item(key = "bought-header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(2.dp))
                                    .clickable { showBought = !showBought }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    (if (showBought) "[-]" else "[+]") + " bought (${bought.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextMuted,
                                )
                            }
                        }
                        if (showBought) {
                            items(bought, key = { "b${it.id}" }) { item ->
                                WishlistBoughtRow(
                                    item = item,
                                    onRestore = { vm.toggleWishlistBought(item) },
                                    onDelete = { confirmDelete = item },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        WishlistItemDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { name, price, store ->
                vm.addWishlistItem(name, price, store)
                showAdd = false
            },
        )
    }

    editing?.let { current ->
        WishlistItemDialog(
            initial = current,
            onDismiss = { editing = null },
            onSave = { name, price, store ->
                vm.updateWishlistItem(current.copy(name = name, bestPrice = price, store = store))
                editing = null
            },
        )
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteWishlistItem(target.id)
                    confirmDelete = null
                }) { Text("Delete", color = RedAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            title = { Text("Remove product?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text("“${target.name}” will be removed from your wishlist.", color = TextSecondary)
            },
            containerColor = DarkCard,
            shape = RoundedCornerShape(2.dp),
        )
    }
}

@Composable
private fun WishlistRow(
    item: WishlistItemRow,
    dragging: Boolean,
    rank: Int,
    modifier: Modifier = Modifier,
    dragHandle: Modifier,
    onClick: () -> Unit,
    onMarkBought: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(2.dp)
    var holding by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val completed = remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(holding) {
        if (holding) {
            completed.value = false
            progress.snapTo(0f)
            // Returns normally only on a full-length hold; an early release cancels it.
            progress.animateTo(1f, tween(HOLD_TO_CHECK_MILLIS, easing = LinearEasing))
            completed.value = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onMarkBought()
            holding = false
        } else {
            progress.animateTo(0f, tween(180))
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = if (dragging) DarkCardLight else DarkCard,
        border = BorderStroke(1.dp, if (dragging) AccentBlueBright else DarkCardLight),
    ) {
        Box {
            // The "buying" fill that grows left→right as the row is held, behind the content.
            Box(Modifier.matchParentSize().clip(shape)) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                        .background(GreenAccent.copy(alpha = 0.22f))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The handle owns reorder; the rest of the row holds-to-buy (tap still edits).
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = if (dragging) AccentBlueBright else TextMuted,
                    modifier = dragHandle.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                holding = true
                                tryAwaitRelease()
                                val heldFraction = progress.value
                                val done = completed.value
                                holding = false
                                // Quick tap → edit; a longer hold released early just cancels.
                                if (!done && heldFraction < 0.08f) onClick()
                            })
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$rank.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 2,
                        )
                        if (!item.store.isNullOrBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Storefront,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    item.store,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    formatPrice(item.bestPrice)?.let { price ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            price,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = GreenAccent,
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** A bought product, receded into the collapsed "bought" section. Restore puts it back on the
 *  wishlist (mirrors un-checking a reminder). */
@Composable
private fun WishlistBoughtRow(
    item: WishlistItemRow,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(2.dp),
        color = DarkBg,
        border = BorderStroke(1.dp, DarkCardLight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusGlyph("[x]", GreenAccent.copy(alpha = 0.45f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textDecoration = TextDecoration.LineThrough,
                    maxLines = 2,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Bought " + formatInstant(item.boughtAtUtc ?: item.createdAtUtc),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted.copy(alpha = 0.7f),
                )
            }
            formatPrice(item.bestPrice)?.let { price ->
                Spacer(Modifier.width(8.dp))
                Text(
                    price,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
            TextButton(onClick = onRestore) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Undo",
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun WishlistItemDialog(
    initial: WishlistItemRow?,
    onDismiss: () -> Unit,
    onSave: (name: String, bestPrice: Double?, store: String) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var priceText by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.bestPrice?.let { formatPlainPrice(it) } ?: "")
    }
    var store by rememberSaveable(initial?.id) { mutableStateOf(initial?.store ?: "") }

    val isEdit = initial != null
    Dialog(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (isEdit) "Edit product" else "New product",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }
            DarkField(value = name, onChange = { name = it }, label = "Product")
            DarkField(
                value = priceText,
                onChange = { priceText = it },
                label = "Best price",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            DarkField(value = store, onChange = { store = it }, label = "Store (optional)")
            Spacer(Modifier.height(4.dp))
            GradientButton(
                label = if (isEdit) "Save changes" else "Add product",
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), parsePrice(priceText), store.trim()) },
            )
        }
    }
}

@Composable
private fun rememberWishlistDragState(
    listState: LazyListState,
    onMove: (Int, Int) -> Unit,
): WishlistDragState = remember(listState) { WishlistDragState(listState, onMove) }

/**
 * Drag-to-reorder bookkeeping for a [LazyColumn]. Tracks which item is held, calls [onMove]
 * when the held item's midpoint crosses a neighbour, and exposes a translation that keeps the
 * dragged row pinned under the finger even as the reorder shifts surrounding offsets.
 */
private class WishlistDragState(
    private val listState: LazyListState,
    private val onMove: (Int, Int) -> Unit,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var draggedDistance by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)

    private val currentItemInfo: LazyListItemInfo?
        get() = draggingItemIndex?.let { idx ->
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
        }

    val draggingItemTranslationY: Float
        get() = currentItemInfo?.let { initialOffset + draggedDistance - it.offset } ?: 0f

    /** Begin dragging the row at [index] (the drag now starts from that row's handle). */
    fun onDragStart(index: Int) {
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == index }
            ?.let {
                draggingItemIndex = index
                initialOffset = it.offset
            }
    }

    fun onDrag(deltaY: Float) {
        draggedDistance += deltaY
        val dragging = currentItemInfo ?: return
        val middle = dragging.offset + draggingItemTranslationY + dragging.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != dragging.index &&
                middle.toInt() in item.offset..(item.offset + item.size)
        }
        if (target != null) {
            onMove(dragging.index, target.index)
            draggingItemIndex = target.index
        }
    }

    fun onDragEnd() {
        draggingItemIndex = null
        draggedDistance = 0f
        initialOffset = 0
    }
}

/** Parse a free-form price ("R$ 1.299,90", "199.90", "50") into a Double, or null if blank. */
private fun parsePrice(text: String): Double? {
    val cleaned = text.trim().replace(Regex("[^0-9.,]"), "")
    if (cleaned.isEmpty()) return null
    val normalized = when {
        cleaned.contains(',') && cleaned.contains('.') -> cleaned.replace(".", "").replace(',', '.')
        cleaned.contains(',') -> cleaned.replace(',', '.')
        else -> cleaned
    }
    return normalized.toDoubleOrNull()
}

/** Currency-formatted price for display, using the device locale. Null when no price set. */
private fun formatPrice(value: Double?): String? =
    value?.let { java.text.NumberFormat.getCurrencyInstance().format(it) }

/** Plain editable price string (no currency symbol) for prefilling the edit field. */
private fun formatPlainPrice(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", value)
