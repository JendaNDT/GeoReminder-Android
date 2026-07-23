package cz.jenda.georeminder.data

import android.content.Context
import android.util.Log
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.notify.ReminderScheduler
import cz.jenda.georeminder.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/**
 * Úložiště připomínek: drží seznam v paměti (StateFlow pro UI), ukládá ho
 * jako JSON na disk (čte ho i widget) a synchronizuje geofence + budíky.
 * Zrcadlí ReminderStore z iOS verze.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scheduler = ReminderScheduler(appContext)

    // Čtení i zápisy jdou přes jedinou frontu. Receiver tak může na načtení
    // opravdu počkat a souběžná UI/receiver změna se navzájem nepřepíše.
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val ioScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // true = poslední čtení dat selhalo → dočasně nepovolit zápis (ochrana proti
    // přepsání platného souboru prázdným seznamem).
    @Volatile
    private var loadFailed = false

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders

    private val _storageError = MutableStateFlow(false)
    val storageError: StateFlow<Boolean> = _storageError

    init {
        reload()
    }

    companion object {
        private const val FILE = "reminders.json"

        @Volatile
        private var instance: ReminderStore? = null

        fun get(context: Context): ReminderStore =
            instance ?: synchronized(this) {
                instance ?: ReminderStore(context).also { instance = it }
            }
    }

    /** Asynchronní reload pro UI. Receivery používají [reloadAndGet]. */
    fun reload() {
        ioScope.launch { reloadFromDisk() }
    }

    /** Garantovaně dokončené načtení pro receiver nebo import. */
    suspend fun reloadAndGet(): List<Reminder> = withContext(ioDispatcher) {
        reloadFromDisk()
    }

    /** Reload a resynchronizace v jedné serializované operaci. */
    fun reloadAndResync() {
        ioScope.launch {
            val loaded = reloadFromDisk()
            if (!loadFailed) scheduler.resync(loaded)
        }
    }

    fun add(reminder: Reminder) {
        ioScope.launch { addOnIo(reminder) }
    }

    suspend fun addDurably(reminder: Reminder): Boolean = withContext(ioDispatcher) {
        addOnIo(reminder)
    }

    fun update(reminder: Reminder) {
        ioScope.launch { updateOnIo(reminder) }
    }

    suspend fun updateDurably(reminder: Reminder): Boolean = withContext(ioDispatcher) {
        updateOnIo(reminder)
    }

    fun toggleDone(reminder: Reminder) {
        update(reminder.copy(isDone = !reminder.isDone))
    }

    fun markDone(reminder: Reminder) {
        if (!reminder.isDone) toggleDone(reminder)
    }

    fun delete(reminder: Reminder) {
        ioScope.launch {
            if (!canWrite()) return@launch
            val next = _reminders.value.filterNot { it.id == reminder.id }
            if (persistSnapshot(next)) {
                _reminders.value = next
                reminder.attachmentPath?.let {
                    AttachmentHelper.deleteAttachment(appContext, it)
                }
                scheduler.cancel(reminder.id)
            }
        }
    }

    /**
     * Akce „Hotovo" z notifikace: načtení, změna i atomický zápis skončí
     * před `PendingResult.finish()`. Vrátí false, pokud se změnu nepodařilo uložit.
     */
    suspend fun markDoneByIdDurably(id: String): Boolean = withContext(ioDispatcher) {
        reloadFromDisk()
        if (!canWrite()) return@withContext false

        val current = _reminders.value
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return@withContext false
        if (current[index].isDone) return@withContext true

        val next = current.toMutableList().apply {
            this[index] = this[index].copy(isDone = true)
        }
        if (persistSnapshot(next)) {
            _reminders.value = next
            scheduler.cancel(id)
            true
        } else {
            false
        }
    }

    /** Jednorázové bezpečné sloučení pro import zálohy. */
    suspend fun upsertAllDurably(imported: List<Reminder>): Boolean =
        withContext(ioDispatcher) {
            reloadFromDisk()
            if (!canWrite()) return@withContext false

            val merged = LinkedHashMap<String, Reminder>()
            _reminders.value.forEach { merged[it.id] = it }
            imported.forEach { merged[it.id] = it }
            val next = merged.values.toList()

            if (persistSnapshot(next)) {
                _reminders.value = next
                scheduler.resync(next)
                true
            } else {
                false
            }
        }

    suspend fun replaceAllDurably(replacement: List<Reminder>): Boolean =
        withContext(ioDispatcher) {
            if (!canWrite()) return@withContext false
            if (persistSnapshot(replacement)) {
                _reminders.value = replacement
                scheduler.resync(replacement)
                true
            } else {
                false
            }
        }

    /** Odloží připomínku – nová jednorázová notifikace za daný počet minut. */
    fun snooze(reminder: Reminder, minutes: Int) {
        scheduler.snooze(reminder, minutes)
    }

    /** Odloží připomínku na konkrétní čas (např. zítra ráno). */
    fun snoozeAt(reminder: Reminder, atMillis: Long) {
        scheduler.snoozeAt(reminder, atMillis)
    }

    /** Znovu zaregistruje geofence a budíky (start appky, po restartu telefonu). */
    fun resyncAll() {
        ioScope.launch {
            if (!loadFailed) scheduler.resync(_reminders.value)
        }
    }

    private fun canWrite(): Boolean {
        if (loadFailed) {
            _storageError.value = true
            Log.w("ReminderStore", "Uložení přeskočeno – poslední čtení dat selhalo")
            return false
        }
        return true
    }

    private fun addOnIo(reminder: Reminder): Boolean {
        if (!canWrite()) return false
        if (_reminders.value.any { it.id == reminder.id }) return false
        val next = _reminders.value + reminder
        if (!persistSnapshot(next)) return false

        _reminders.value = next
        reminder.attachmentPath?.let { AttachmentHelper.markPersisted(it) }
        scheduler.schedule(reminder)
        return true
    }

    private fun updateOnIo(reminder: Reminder): Boolean {
        if (!canWrite()) return false
        val list = _reminders.value.toMutableList()
        val index = list.indexOfFirst { it.id == reminder.id }
        if (index < 0) return false
        val oldReminder = list[index]
        list[index] = reminder
        if (!persistSnapshot(list)) return false

        _reminders.value = list
        reminder.attachmentPath?.let { AttachmentHelper.markPersisted(it) }
        if (oldReminder.attachmentPath != null &&
            oldReminder.attachmentPath != reminder.attachmentPath
        ) {
            AttachmentHelper.deleteAttachment(appContext, oldReminder.attachmentPath)
        }
        scheduler.cancel(reminder.id)
        if (!reminder.isDone) scheduler.schedule(reminder)
        return true
    }

    /** Musí být voláno z [ioDispatcher]. */
    private fun reloadFromDisk(): List<Reminder> {
        when (val result = SharedStorage.read(appContext, FILE)) {
            is SharedStorage.ReadResult.Ok -> {
                when (val decoded = SharedStorage.decodeRemindersResult(result.text)) {
                    is SharedStorage.DecodeResult.Ok -> {
                        _reminders.value = decoded.value
                        loadFailed = decoded.hadInvalidEntries
                        _storageError.value = decoded.hadInvalidEntries
                        if (!decoded.hadInvalidEntries) {
                            AttachmentHelper.cleanupOrphanedAttachments(appContext, decoded.value)
                        } else {
                            Log.w(
                                "ReminderStore",
                                "Některé záznamy jsou poškozené – automatický zápis je zablokován"
                            )
                        }
                    }
                    SharedStorage.DecodeResult.Error -> {
                        loadFailed = true
                        _storageError.value = true
                        Log.w("ReminderStore", "Dekódování dat selhalo – soubor nebude přepsán")
                    }
                }
            }
            SharedStorage.ReadResult.Empty -> {
                _reminders.value = emptyList()
                loadFailed = false
                _storageError.value = false
            }
            SharedStorage.ReadResult.Error -> {
                loadFailed = true
                _storageError.value = true
                Log.w("ReminderStore", "Čtení dat selhalo – soubor nebude přepsán")
            }
        }
        return _reminders.value
    }

    /** Musí být voláno z [ioDispatcher]. */
    private fun persistSnapshot(snapshot: List<Reminder>): Boolean = try {
        val text = SharedStorage.json.encodeToString(
            ListSerializer(Reminder.serializer()), snapshot
        )
        SharedStorage.writeText(appContext, FILE, text).also { success ->
            _storageError.value = !success
            if (success) {
                WidgetRefresher.refresh(appContext)
            }
        }
    } catch (e: Exception) {
        _storageError.value = true
        Log.w("ReminderStore", "Chyba při sériovém zápisu na disk", e)
        false
    }
}
