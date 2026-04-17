package com.reminder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reminder.data.OccurrenceRow
import com.reminder.data.ReminderRow
import com.reminder.data.ScheduleKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderApp(vm: ReminderViewModel) {
    val reminders by vm.reminders.collectAsState()
    val pending by vm.pendingOccurrences.collectAsState()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Reminders") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pending.isNotEmpty()) {
                item { SectionHeader("Due — check them off") }
                items(pending, key = { "p${it.id}" }) { occ ->
                    val reminder = reminders.firstOrNull { it.id == occ.reminderLocalId }
                    PendingRow(occ, reminder?.description ?: "Reminder") { vm.check(occ) }
                }
            }
            item { SectionHeader("All reminders") }
            items(reminders, key = { "r${it.id}" }) { r ->
                ReminderRowCard(
                    r,
                    onToggle = { vm.toggleActive(r) },
                    onDelete = { vm.delete(r) },
                )
            }
        }
    }

    if (showCreate) {
        CreateReminderDialog(
            onDismiss = { showCreate = false },
            onCreate = { description, kind, minuteOfDay, oneTimeUtc ->
                vm.createReminder(description, kind, minuteOfDay, oneTimeUtc)
                showCreate = false
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun PendingRow(occ: OccurrenceRow, description: String, onCheck: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = false, onCheckedChange = { onCheck() })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(description, style = MaterialTheme.typography.bodyLarge)
                Text("Due " + formatInstant(occ.dueAtUtc),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReminderRowCard(r: ReminderRow, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(r.description, style = MaterialTheme.typography.bodyLarge)
                Text(scheduleSummary(r), style = MaterialTheme.typography.bodySmall)
                if (r.serverId == null) {
                    Text("· not synced yet", style = MaterialTheme.typography.labelSmall)
                }
            }
            Switch(checked = r.isActive, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

private fun scheduleSummary(r: ReminderRow): String = when (r.scheduleKind) {
    ScheduleKind.Daily -> {
        val m = r.dailyMinuteOfDay ?: 0
        "Daily at %02d:%02d".format(m / 60, m % 60)
    }
    ScheduleKind.OneTime -> r.oneTimeDueAtUtc?.let { "Once on " + formatInstant(it) } ?: "Once"
}

private fun formatInstant(epochMillis: Long): String {
    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
    return ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateReminderDialog(
    onDismiss: () -> Unit,
    onCreate: (description: String, kind: ScheduleKind, dailyMinuteOfDay: Int?, oneTimeUtc: Long?) -> Unit,
) {
    var description by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(ScheduleKind.Daily) }
    var hour by rememberSaveable { mutableStateOf(9) }
    var minute by rememberSaveable { mutableStateOf(0) }
    var year by rememberSaveable { mutableStateOf(LocalDate.now().year) }
    var month by rememberSaveable { mutableStateOf(LocalDate.now().monthValue) }
    var day by rememberSaveable { mutableStateOf(LocalDate.now().dayOfMonth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = description.isNotBlank(),
                onClick = {
                    when (kind) {
                        ScheduleKind.Daily -> onCreate(description.trim(), kind, hour * 60 + minute, null)
                        ScheduleKind.OneTime -> {
                            val ldt = LocalDateTime.of(
                                LocalDate.of(year, month, day),
                                LocalTime.of(hour, minute),
                            )
                            val millis = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            onCreate(description.trim(), kind, null, millis)
                        }
                    }
                }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("New reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Frequency:", modifier = Modifier.weight(1f))
                    FilterChip(selected = kind == ScheduleKind.Daily,
                        onClick = { kind = ScheduleKind.Daily }, label = { Text("Daily") })
                    Spacer(Modifier.width(4.dp))
                    FilterChip(selected = kind == ScheduleKind.OneTime,
                        onClick = { kind = ScheduleKind.OneTime }, label = { Text("Once") })
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Time:")
                    NumberField("HH", hour, 0, 23) { hour = it }
                    Text(":")
                    NumberField("mm", minute, 0, 59) { minute = it }
                }
                if (kind == ScheduleKind.OneTime) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Date:")
                        NumberField("Y", year, 2020, 2100) { year = it }
                        Text("-")
                        NumberField("M", month, 1, 12) { month = it }
                        Text("-")
                        NumberField("D", day, 1, 31) { day = it }
                    }
                }
            }
        }
    )
}

@Composable
private fun NumberField(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.filter { c -> c.isDigit() }
            text.toIntOrNull()?.let { n -> if (n in min..max) onChange(n) }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.width(76.dp),
    )
}
