package com.reminder.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.reminder.data.AppDatabase
import com.reminder.data.OccurrenceRow
import com.reminder.data.ReminderRow
import com.reminder.data.ScheduleKind
import com.reminder.network.ApiClient
import com.reminder.network.CreateReminderRequest
import com.reminder.network.FireOccurrenceRequest
import com.reminder.network.UpdateReminderRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Local-first sync. Every write hits Room immediately; this pushes queued changes to the
 * server, then pulls the server state back and merges by serverId.
 */
class SyncManager private constructor(private val appContext: Context) {
    private val db = AppDatabase.get(appContext)
    private val api = ApiClient.api
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun start() {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { triggerSync() }
        })
        triggerSync()
    }

    fun triggerSync() {
        scope.launch { runCatching { syncOnce() }.onFailure { Log.w(TAG, "sync failed", it) } }
    }

    suspend fun syncOnce() = mutex.withLock {
        pushReminders()
        pushOccurrences()
        pullReminders()
        pullOccurrences()
    }

    private suspend fun pushReminders() {
        val rDao = db.reminders()
        for (r in rDao.pending()) {
            try {
                when {
                    r.pendingDelete && r.serverId != null -> {
                        api.delete(r.serverId)
                        rDao.deleteByLocalId(r.id)
                    }
                    r.pendingDelete && r.serverId == null -> {
                        rDao.deleteByLocalId(r.id)
                    }
                    r.pendingCreate && r.serverId == null -> {
                        val dto = api.create(CreateReminderRequest(
                            description = r.description,
                            scheduleKind = r.scheduleKind.api,
                            dailyMinuteOfDay = r.dailyMinuteOfDay,
                            oneTimeDueAtUtc = r.oneTimeDueAtUtc?.let { Instant.ofEpochMilli(it).toString() },
                        ))
                        rDao.update(r.copy(
                            serverId = dto.id,
                            pendingCreate = false,
                            pendingUpdate = false,
                        ))
                    }
                    r.pendingUpdate && r.serverId != null -> {
                        api.update(r.serverId, UpdateReminderRequest(
                            description = r.description,
                            scheduleKind = r.scheduleKind.api,
                            dailyMinuteOfDay = r.dailyMinuteOfDay,
                            oneTimeDueAtUtc = r.oneTimeDueAtUtc?.let { Instant.ofEpochMilli(it).toString() },
                            isActive = r.isActive,
                        ))
                        rDao.update(r.copy(pendingUpdate = false))
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "push reminder ${r.id} failed", t)
            }
        }
    }

    private suspend fun pushOccurrences() {
        val oDao = db.occurrences()
        val rDao = db.reminders()
        for (o in oDao.pending()) {
            try {
                if (o.pendingCreate && o.serverId == null) {
                    val reminder = rDao.findByLocalId(o.reminderLocalId) ?: continue
                    val serverReminderId = reminder.serverId ?: continue // wait until reminder is synced
                    val dto = api.fire(serverReminderId, FireOccurrenceRequest(
                        dueAtUtc = Instant.ofEpochMilli(o.dueAtUtc).toString()
                    ))
                    oDao.update(o.copy(serverId = dto.id, pendingCreate = false))
                }
                val fresh = oDao.findByLocalId(o.id) ?: continue
                if (fresh.pendingCheck && fresh.serverId != null && fresh.checkedAtUtc != null) {
                    api.check(fresh.serverId)
                    oDao.update(fresh.copy(pendingCheck = false))
                }
            } catch (t: Throwable) {
                Log.w(TAG, "push occurrence ${o.id} failed", t)
            }
        }
    }

    private suspend fun pullReminders() {
        val rDao = db.reminders()
        val serverList = try { api.list() } catch (t: Throwable) { Log.w(TAG, "pull reminders", t); return }
        for (dto in serverList) {
            val existing = rDao.findByServerId(dto.id)
            if (existing == null) {
                rDao.insert(ReminderRow(
                    serverId = dto.id,
                    description = dto.description,
                    scheduleKind = ScheduleKind.values().first { it.api == dto.scheduleKind },
                    dailyMinuteOfDay = dto.dailyMinuteOfDay,
                    oneTimeDueAtUtc = dto.oneTimeDueAtUtc?.let { Instant.parse(it).toEpochMilli() },
                    isActive = dto.isActive,
                    pendingCreate = false,
                ))
            } else if (!existing.pendingUpdate && !existing.pendingDelete) {
                rDao.update(existing.copy(
                    description = dto.description,
                    scheduleKind = ScheduleKind.values().first { it.api == dto.scheduleKind },
                    dailyMinuteOfDay = dto.dailyMinuteOfDay,
                    oneTimeDueAtUtc = dto.oneTimeDueAtUtc?.let { Instant.parse(it).toEpochMilli() },
                    isActive = dto.isActive,
                ))
            }
        }
    }

    private suspend fun pullOccurrences() {
        val oDao = db.occurrences()
        val rDao = db.reminders()
        val serverList = try { api.pendingOccurrences() } catch (t: Throwable) { Log.w(TAG, "pull occ", t); return }
        for (dto in serverList) {
            val reminderLocal = rDao.findByServerId(dto.reminderId) ?: continue
            val existing = db.occurrences().pending().firstOrNull { it.serverId == dto.id }
                ?: findByServerId(dto.id)
            if (existing == null) {
                oDao.insert(OccurrenceRow(
                    serverId = dto.id,
                    reminderLocalId = reminderLocal.id,
                    dueAtUtc = Instant.parse(dto.dueAtUtc).toEpochMilli(),
                    firedAtUtc = Instant.parse(dto.firedAtUtc).toEpochMilli(),
                    checkedAtUtc = dto.checkedAtUtc?.let { Instant.parse(it).toEpochMilli() },
                    pendingCreate = false,
                ))
            }
        }
    }

    private suspend fun findByServerId(serverId: Int): OccurrenceRow? {
        // No dedicated query — scan pending is good enough for this small dataset; a larger
        // app would add a DAO query.
        return db.occurrences().pending().firstOrNull { it.serverId == serverId }
    }

    companion object {
        private const val TAG = "SyncManager"
        @Volatile private var INSTANCE: SyncManager? = null
        fun get(ctx: Context): SyncManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(ctx.applicationContext).also { INSTANCE = it }
            }
    }
}
