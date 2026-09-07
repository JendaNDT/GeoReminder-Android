package cz.jenda.georeminder.notify

import android.content.Context
import android.content.SharedPreferences
import cz.jenda.georeminder.data.SharedStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Jediný perzistentní zdroj pravdy pro technický stav plánování reminderů.
 *
 * Uživatelská data patří do ReminderStore. Sem patří jen technické značky,
 * které musí přežít restart procesu/telefonu: jednorázové „už vystřeleno",
 * snooze timestamp, stabilní requestCode pro PendingIntent a stav geofence.
 */
internal class SchedulerStateStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)

    private val _geofenceStates = MutableStateFlow(loadGeofenceStates())
    val geofenceStates: StateFlow<Map<String, GeofenceRegistrationState>> =
        _geofenceStates.asStateFlow()

    init {
        migrateLegacyFiredState()
    }

    companion object {
        private const val KEY_FIRED_ONE_TIME = "scheduler_fired_one_time"
        private const val LEGACY_FIRED_GEOFENCES = "firedGeofenceIds"
        private const val LEGACY_FIRED_ALARMS = "firedAlarmIds"
        private const val KEY_SNOOZE_PREFIX = "snooze_"
        private const val KEY_REQUEST_CODE_PREFIX = "scheduler_request_code_"
        private const val KEY_NEXT_REQUEST_CODE = "scheduler_next_request_code"
        private const val KEY_GEOFENCE_STATE_PREFIX = "scheduler_geofence_state_"

        private const val REQUEST_CODE_START = 10_000
        private const val REQUEST_CODE_STRIDE = 8

        const val OFFSET_ALARM = 0
        const val OFFSET_SNOOZE = 1
        const val OFFSET_NAG = 2

        private val lock = Any()
    }

    private fun migrateLegacyFiredState() = synchronized(lock) {
        val current = prefs.getStringSet(KEY_FIRED_ONE_TIME, emptySet()).orEmpty()
        val legacyGeo = prefs.getStringSet(LEGACY_FIRED_GEOFENCES, emptySet()).orEmpty()
        val legacyAlarm = prefs.getStringSet(LEGACY_FIRED_ALARMS, emptySet()).orEmpty()
        if (legacyGeo.isEmpty() && legacyAlarm.isEmpty()) return@synchronized

        prefs.edit()
            .putStringSet(KEY_FIRED_ONE_TIME, HashSet(current + legacyGeo + legacyAlarm))
            .remove(LEGACY_FIRED_GEOFENCES)
            .remove(LEGACY_FIRED_ALARMS)
            .apply()
    }

    fun isFired(reminderId: String): Boolean = synchronized(lock) {
        reminderId in prefs.getStringSet(KEY_FIRED_ONE_TIME, emptySet()).orEmpty()
    }

    fun markFired(reminderId: String) = synchronized(lock) {
        val current = prefs.getStringSet(KEY_FIRED_ONE_TIME, emptySet()).orEmpty()
        if (reminderId in current) return@synchronized
        prefs.edit()
            .putStringSet(KEY_FIRED_ONE_TIME, HashSet(current).apply { add(reminderId) })
            .apply()
    }

    fun clearFired(reminderId: String) = synchronized(lock) {
        val current = prefs.getStringSet(KEY_FIRED_ONE_TIME, emptySet()).orEmpty()
        if (reminderId !in current) return@synchronized
        prefs.edit()
            .putStringSet(KEY_FIRED_ONE_TIME, HashSet(current).apply { remove(reminderId) })
            .apply()
    }

    fun setSnooze(reminderId: String, atMillis: Long) = synchronized(lock) {
        prefs.edit().putLong(KEY_SNOOZE_PREFIX + reminderId, atMillis).apply()
    }

    fun snoozeUntil(reminderId: String): Long? = synchronized(lock) {
        prefs.getLong(KEY_SNOOZE_PREFIX + reminderId, 0L).takeIf { it > 0L }
    }

    fun clearSnooze(reminderId: String) = synchronized(lock) {
        prefs.edit().remove(KEY_SNOOZE_PREFIX + reminderId).apply()
    }

    fun allSnoozes(): Map<String, Long> = synchronized(lock) {
        prefs.all
            .asSequence()
            .filter { (key, value) -> key.startsWith(KEY_SNOOZE_PREFIX) && value is Long }
            .mapNotNull { (key, value) ->
                val id = key.removePrefix(KEY_SNOOZE_PREFIX)
                val at = value as? Long ?: return@mapNotNull null
                if (id.isBlank() || at <= 0L) null else id to at
            }
            .toMap()
    }

    fun setGeofenceState(
        reminderId: String,
        status: GeofenceRegistrationStatus,
        errorCode: Int? = null,
    ) = synchronized(lock) {
        val state = GeofenceRegistrationState(
            status = status,
            updatedAt = System.currentTimeMillis(),
            errorCode = errorCode,
        )
        prefs.edit()
            .putString(KEY_GEOFENCE_STATE_PREFIX + reminderId, encodeGeofenceState(state))
            .apply()
        _geofenceStates.value = _geofenceStates.value + (reminderId to state)
    }

    fun clearGeofenceState(reminderId: String) = synchronized(lock) {
        if (reminderId !in _geofenceStates.value) return@synchronized
        prefs.edit().remove(KEY_GEOFENCE_STATE_PREFIX + reminderId).apply()
        _geofenceStates.value = _geofenceStates.value - reminderId
    }

    /** Odstraní diagnostické stavy reminderů, které už nejsou aktivní location remindery. */
    fun retainGeofenceStates(reminderIds: Set<String>) = synchronized(lock) {
        val staleIds = _geofenceStates.value.keys - reminderIds
        if (staleIds.isEmpty()) return@synchronized

        val editor = prefs.edit()
        staleIds.forEach { editor.remove(KEY_GEOFENCE_STATE_PREFIX + it) }
        editor.apply()
        _geofenceStates.value = _geofenceStates.value.filterKeys { it in reminderIds }
    }

    fun activeGeofenceCount(excludingReminderId: String? = null): Int = synchronized(lock) {
        _geofenceStates.value.count { (id, state) ->
            id != excludingReminderId && state.status == GeofenceRegistrationStatus.ACTIVE
        }
    }

    /**
     * Vrací stabilní unikátní základ requestCode pro reminder. Každý reminder
     * dostane blok několika integerů (alarm/snooze/nag), takže jednotlivé typy
     * PendingIntentů nemohou kolidovat ani při shodě String.hashCode().
     * Čítač je perzistentní, takže stejné ID používá stejný blok i po rebootu.
     */
    fun requestCode(reminderId: String, offset: Int): Int = synchronized(lock) {
        require(offset in 0 until REQUEST_CODE_STRIDE)
        val key = KEY_REQUEST_CODE_PREFIX + reminderId
        var base = prefs.getInt(key, 0)
        if (base == 0) {
            base = prefs.getInt(KEY_NEXT_REQUEST_CODE, REQUEST_CODE_START)
                .coerceAtLeast(REQUEST_CODE_START)
            prefs.edit()
                .putInt(key, base)
                .putInt(KEY_NEXT_REQUEST_CODE, base + REQUEST_CODE_STRIDE)
                .apply()
        }
        base + offset
    }

    private fun loadGeofenceStates(): Map<String, GeofenceRegistrationState> {
        return prefs.all
            .asSequence()
            .filter { (key, value) ->
                key.startsWith(KEY_GEOFENCE_STATE_PREFIX) && value is String
            }
            .mapNotNull { (key, value) ->
                val reminderId = key.removePrefix(KEY_GEOFENCE_STATE_PREFIX)
                val raw = value as? String ?: return@mapNotNull null
                if (reminderId.isBlank()) return@mapNotNull null
                decodeGeofenceState(raw)?.let { reminderId to it }
            }
            .toMap()
    }

    private fun encodeGeofenceState(state: GeofenceRegistrationState): String =
        listOf(
            state.status.name,
            state.errorCode?.toString().orEmpty(),
            state.updatedAt.toString(),
        ).joinToString("|")

    private fun decodeGeofenceState(raw: String): GeofenceRegistrationState? {
        val parts = raw.split('|')
        val status = runCatching {
            GeofenceRegistrationStatus.valueOf(parts.getOrNull(0).orEmpty())
        }.getOrNull() ?: return null
        val errorCode = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toIntOrNull()
        val updatedAt = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        return GeofenceRegistrationState(status, updatedAt, errorCode)
    }
}
