package cz.jenda.georeminder.data

import android.content.Context
import android.util.Log
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.notify.ReminderScheduler
import cz.jenda.georeminder.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/**
 * Úložiště připomínek: drží seznam v paměti, ukládá ho jako JSON na disk
 * a synchronizuje geofence + budíky.
 */
class ReminderStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scheduler = ReminderScheduler.get(appContext)
    private val diagnostics = DiagnosticStore.get(appContext)

    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val ioScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    @Volatile
    private var loadFailed = false

    @Volatile
    private var hasLoadedSuccessfully = false

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders

    enum class DataIntegrityState {
        OK,
        PARTIAL_RECOVERED,
        CORRUPTED,
    }

    private val _dataIntegrityState = MutableStateFlow(DataIntegrityState.OK)
    val dataIntegrityState: StateFlow<DataIntegrityState> = _dataIntegrityState

    init {
        reload()
    }

    enum class ReloadResult {
        LOADED,
        RECOVERED,
        EMPTY,
        CACHED,
        ERROR,
    }

    companion object {
        const val FILE = "reminders.json"

        @Volatile
        private var instance: ReminderStore? = null

        fun get(context: Context): ReminderStore =
            instance ?: synchronized(this) {
                instance ?: ReminderStore(context).also { instance = it }
            }
    }

    fun reload() {
        ioScope.launch {
            reloadAndWait()
        }
    }

    suspend fun reloadAndWait(): ReloadResult = withContext(ioDispatcher) {
        synchronized(this@ReminderStore) {
            when (val res = SharedStorage.read(appContext, FILE)) {
                is SharedStorage.ReadResult.Ok -> {
                    when (val decoded = SharedStorage.decodeReminders(res.text)) {
                        is SharedStorage.DecodeRemindersResult.Success -> {
                            val normalized = normalizeAttachmentPaths(decoded.reminders)
                            _reminders.value = normalized
                            loadFailed = false
                            hasLoadedSuccessfully = true
                            if (_dataIntegrityState.value != DataIntegrityState.PARTIAL_RECOVERED) {
                                _dataIntegrityState.value = DataIntegrityState.OK
                            }
                            ReloadResult.LOADED
                        }

                        is SharedStorage.DecodeRemindersResult.Partial -> {
                            val recovered = normalizeAttachmentPaths(decoded.reminders)
                            _reminders.value = recovered
                            hasLoadedSuccessfully = true
                            _dataIntegrityState.value = DataIntegrityState.PARTIAL_RECOVERED
                            diagnostics.record(
                                DiagnosticEventType.DATA_PARTIAL_RECOVERY,
                                "recovered=${recovered.size} skipped=${decoded.skippedCount}",
                            )

                            val recoveryCopy = SharedStorage.preserveCorruptCopy(
                                appContext,
                                FILE,
                                res.text,
                            )
                            if (recoveryCopy == null) {
                                loadFailed = true
                                Log.w(
                                    "ReminderStore",
                                    "Částečná obnova načtena jen pro čtení – recovery kopie selhala",
                                )
                                return@synchronized ReloadResult.RECOVERED
                            }

                            val recoveredText = SharedStorage.json.encodeToString(
                                ListSerializer(Reminder.serializer()),
                                recovered,
                            )
                            val repaired = SharedStorage.writeText(appContext, FILE, recoveredText)
                            loadFailed = !repaired
                            if (repaired) {
                                Log.w(
                                    "ReminderStore",
                                    "Obnoveno ${recovered.size} připomínek; přeskočeno ${decoded.skippedCount}. " +
                                        "Původní data: ${recoveryCopy.name}",
                                )
                            } else {
                                Log.w(
                                    "ReminderStore",
                                    "Částečná data načtena, ale opravený reminders.json se nepodařilo zapsat",
                                )
                            }
                            ReloadResult.RECOVERED
                        }

                        is SharedStorage.DecodeRemindersResult.Corrupted -> {
                            _dataIntegrityState.value = DataIntegrityState.CORRUPTED
                            SharedStorage.preserveCorruptCopy(appContext, FILE, res.text)
                            loadFailed = true
                            Log.w(
                                "ReminderStore",
                                "reminders.json je nečitelný (${decoded.reason}) – původní soubor nebyl přepsán",
                            )
                            if (hasLoadedSuccessfully) ReloadResult.CACHED else ReloadResult.ERROR
                        }
                    }
                }

                SharedStorage.ReadResult.Empty -> {
                    _reminders.value = emptyList()
                    loadFailed = false
                    hasLoadedSuccessfully = true
                    _dataIntegrityState.value = DataIntegrityState.OK
                    ReloadResult.EMPTY
                }

                SharedStorage.ReadResult.Error -> {
                    loadFailed = true
                    Log.w("ReminderStore", "Čtení dat selhalo – uložení dočasně zablokováno")
                    if (hasLoadedSuccessfully) ReloadResult.CACHED else ReloadResult.ERROR
                }
            }
        }
    }

    private fun normalizeAttachmentPaths(reminders: List<Reminder>): List<Reminder> =
        reminders.map { reminder ->
            val normalized = AttachmentHelper.normalizeRestoredAttachmentPath(
                appContext,
                reminder.attachmentPath,
            )
            if (normalized == reminder.attachmentPath) reminder
            else reminder.copy(attachmentPath = normalized)
        }

    @Synchronized
    fun add(reminder: Reminder) {
        _reminders.value = _reminders.value + reminder
        persist()
        if (reminder.kind == ReminderKind.LOCATION) {
            scheduler.resyncGeofences(_reminders.value)
        } else {
            scheduler.schedule(reminder)
            diagnostics.record(DiagnosticEventType.ALARM_SCHEDULED)
        }
    }

    @Synchronized
    fun update(reminder: Reminder) {
        val list = _reminders.value.toMutableList()
        val index = list.indexOfFirst { it.id == reminder.id }
        if (index < 0) return
        val oldReminder = list[index]
        if (oldReminder.attachmentPath != null && oldReminder.attachmentPath != reminder.attachmentPath) {
            AttachmentHelper.deleteAttachment(appContext, oldReminder.attachmentPath)
        }
        list[index] = reminder
        _reminders.value = list
        persist()
        scheduler.cancel(reminder.id)

        val touchesGeofences =
            oldReminder.kind == ReminderKind.LOCATION || reminder.kind == ReminderKind.LOCATION

        if (!reminder.isDone && reminder.kind == ReminderKind.TIME) {
            scheduler.schedule(reminder)
            diagnostics.record(DiagnosticEventType.ALARM_SCHEDULED)
        }
        if (touchesGeofences) {
            scheduler.resyncGeofences(_reminders.value)
        }
    }

    fun toggleDone(reminder: Reminder) {
        update(reminder.copy(isDone = !reminder.isDone))
    }

    fun markDone(reminder: Reminder) {
        if (!reminder.isDone) toggleDone(reminder)
    }

    @Synchronized
    fun delete(reminder: Reminder) {
        _reminders.value = _reminders.value.filterNot { it.id == reminder.id }
        if (reminder.attachmentPath != null) {
            AttachmentHelper.deleteAttachment(appContext, reminder.attachmentPath)
        }
        scheduler.cancel(reminder.id)
        persist()
        if (reminder.kind == ReminderKind.LOCATION) {
            scheduler.resyncGeofences(_reminders.value)
        }
    }

    fun snooze(reminder: Reminder, minutes: Int) {
        scheduler.snooze(reminder, minutes)
        if (reminder.kind == ReminderKind.LOCATION) {
            scheduler.resyncGeofences(_reminders.value)
        }
    }

    fun snoozeAt(reminder: Reminder, atMillis: Long) {
        scheduler.snoozeAt(reminder, atMillis)
        if (reminder.kind == ReminderKind.LOCATION) {
            scheduler.resyncGeofences(_reminders.value)
        }
    }

    fun resyncAll() {
        try {
            scheduler.resync(_reminders.value)
            diagnostics.markResyncSuccess()
        } catch (error: Exception) {
            diagnostics.markResyncFailure(error)
            throw error
        }
    }

    suspend fun snapshotAfterPendingIo(): List<Reminder> = withContext(ioDispatcher) {
        _reminders.value
    }

    suspend fun replaceAllFromImport(snapshot: List<Reminder>): Boolean =
        withContext(ioDispatcher) {
            try {
                val text = SharedStorage.json.encodeToString(
                    ListSerializer(Reminder.serializer()),
                    snapshot,
                )
                if (!SharedStorage.writeText(appContext, FILE, text)) return@withContext false

                synchronized(this@ReminderStore) {
                    _reminders.value = snapshot
                    loadFailed = false
                    hasLoadedSuccessfully = true
                    _dataIntegrityState.value = DataIntegrityState.OK
                }
                scheduler.resync(snapshot)
                diagnostics.markResyncSuccess()
                WidgetRefresher.refresh(appContext)
                true
            } catch (e: Exception) {
                diagnostics.markResyncFailure(e)
                Log.w("ReminderStore", "Dávkové nahrazení dat po importu selhalo", e)
                false
            }
        }

    private fun persist() {
        if (loadFailed) {
            Log.w("ReminderStore", "Uložení přeskočeno – poslední čtení dat selhalo")
            return
        }
        val snapshot = _reminders.value
        ioScope.launch {
            try {
                val text = SharedStorage.json.encodeToString(
                    ListSerializer(Reminder.serializer()), snapshot
                )
                if (SharedStorage.writeText(appContext, FILE, text)) {
                    WidgetRefresher.refresh(appContext)
                }
            } catch (e: Exception) {
                Log.w("ReminderStore", "Chyba při sériovém zápisu na disk", e)
            }
        }
    }
}
