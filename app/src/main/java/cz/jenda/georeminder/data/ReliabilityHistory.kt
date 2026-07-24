package cz.jenda.georeminder.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

@Serializable
enum class ReliabilityEventType {
    SCHEDULED_EXACT,
    SCHEDULED_APPROXIMATE,
    GEOFENCE_REGISTERED,
    GEOFENCE_FAILED,
    TRIGGERED_TIME,
    TRIGGERED_LOCATION,
    NOTIFICATION_SHOWN,
    NOTIFICATION_BLOCKED,
    SNOOZED,
    COMPLETED,
    REACTIVATED,
    DELETED,
    TEST_SCHEDULED,
    TEST_TRIGGERED,
}

@Serializable
data class ReliabilityEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: ReliabilityEventType,
    val timestamp: Long = System.currentTimeMillis(),
    val reminderId: String? = null,
    val reminderTitle: String? = null,
    val detail: String? = null,
)

/**
 * Krátká lokální diagnostická historie. Neobsahuje souřadnice ani přílohy a
 * nikdy neopouští zařízení. Zápisy jsou serializované na jednom IO vlákně.
 */
object ReliabilityHistory {
    private const val FILE = "reliability_events.json"
    private const val MAX_EVENTS = 100
    private const val DEDUPE_WINDOW_MS = 30_000L

    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _events = MutableStateFlow<List<ReliabilityEvent>>(emptyList())
    val events: StateFlow<List<ReliabilityEvent>> = _events

    private var loaded = false

    fun initialize(context: Context) {
        scope.launch {
            ensureLoaded(context.applicationContext)
        }
    }

    fun record(
        context: Context,
        type: ReliabilityEventType,
        reminderId: String? = null,
        reminderTitle: String? = null,
        detail: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        val event = ReliabilityEvent(
            type = type,
            timestamp = timestamp,
            reminderId = reminderId,
            reminderTitle = reminderTitle,
            detail = detail,
        )
        scope.launch {
            val appContext = context.applicationContext
            ensureLoaded(appContext)
            val updated = appendEvent(_events.value, event)
            if (updated !== _events.value) {
                _events.value = updated
                persist(appContext, updated)
            }
        }
    }

    fun clear(context: Context) {
        scope.launch {
            val appContext = context.applicationContext
            ensureLoaded(appContext)
            _events.value = emptyList()
            persist(appContext, emptyList())
        }
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        _events.value = when (val result = SharedStorage.read(context, FILE)) {
            is SharedStorage.ReadResult.Ok -> {
                try {
                    SharedStorage.json.decodeFromString(
                        ListSerializer(ReliabilityEvent.serializer()),
                        result.text,
                    ).sortedByDescending { it.timestamp }.take(MAX_EVENTS)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            SharedStorage.ReadResult.Empty,
            SharedStorage.ReadResult.Error -> emptyList()
        }
        loaded = true
    }

    private fun persist(context: Context, events: List<ReliabilityEvent>) {
        val json = SharedStorage.json.encodeToString(
            ListSerializer(ReliabilityEvent.serializer()),
            events,
        )
        SharedStorage.writeText(context, FILE, json)
    }

    internal fun appendEvent(
        current: List<ReliabilityEvent>,
        event: ReliabilityEvent,
        maxEvents: Int = MAX_EVENTS,
    ): List<ReliabilityEvent> {
        val latest = current.firstOrNull()
        val isDuplicate = latest != null &&
                latest.type == event.type &&
                latest.reminderId == event.reminderId &&
                latest.detail == event.detail &&
                event.timestamp - latest.timestamp in 0..DEDUPE_WINDOW_MS
        if (isDuplicate) return current
        return (listOf(event) + current).take(maxEvents)
    }
}
