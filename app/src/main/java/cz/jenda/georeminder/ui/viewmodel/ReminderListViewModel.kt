package cz.jenda.georeminder.ui.viewmodel

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import cz.jenda.georeminder.data.LocationHolder
import cz.jenda.georeminder.data.PlaceLinkResolver
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.model.CzechFormat
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ReminderListViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ReminderStore.get(application)

    val reminders: StateFlow<List<Reminder>> = store.reminders
    val userLocation: StateFlow<Location?> = LocationHolder.location
    val geofenceFailed: StateFlow<Boolean> = LocationHolder.geofenceFailed

    private val pendingDeleteIds = MutableStateFlow<Set<String>>(emptySet())
    val searchQuery = MutableStateFlow("")

    val activeReminders: StateFlow<List<Reminder>> = combine(reminders, pendingDeleteIds, searchQuery) { list, pending, query ->
        val q = query.trim().lowercase()
        list.filter { !it.isDone && it.id !in pending }
            .filter { reminder ->
                if (q.isEmpty()) true
                else reminder.title.lowercase().contains(q) || reminder.placeName.lowercase().contains(q)
            }
            .sortedByDescending { it.createdAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val doneReminders: StateFlow<List<Reminder>> = combine(reminders, pendingDeleteIds, searchQuery) { list, pending, query ->
        val q = query.trim().lowercase()
        list.filter { it.isDone && it.id !in pending }
            .filter { reminder ->
                if (q.isEmpty()) true
                else reminder.title.lowercase().contains(q) || reminder.placeName.lowercase().contains(q)
            }
            .sortedByDescending { it.createdAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        searchQuery.value = query.take(200)
    }

    fun distanceText(reminder: Reminder): String? {
        if (reminder.kind != ReminderKind.LOCATION) return null
        val user = userLocation.value ?: return null
        val result = FloatArray(1)
        Location.distanceBetween(
            user.latitude, user.longitude,
            reminder.latitude, reminder.longitude, result,
        )
        return CzechFormat.distance(result[0])
    }

    fun toggleDone(reminder: Reminder) {
        store.toggleDone(reminder)
    }

    fun markPendingDelete(reminder: Reminder) {
        pendingDeleteIds.value = pendingDeleteIds.value + reminder.id
    }

    fun cancelPendingDelete(reminder: Reminder) {
        pendingDeleteIds.value = pendingDeleteIds.value - reminder.id
    }

    fun confirmDelete(reminder: Reminder) {
        pendingDeleteIds.value = pendingDeleteIds.value - reminder.id
        store.delete(reminder)
    }

    suspend fun resolveSharedPlace(text: String): Pair<String, LatLng>? {
        return PlaceLinkResolver.resolve(text)
    }
}
