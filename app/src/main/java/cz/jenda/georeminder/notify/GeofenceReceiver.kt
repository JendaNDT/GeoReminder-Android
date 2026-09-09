package cz.jenda.georeminder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Přijímá geofence události (příjezd/odjezd) a zobrazuje notifikace.
 * Jednorázová připomínka po spuštění geofence odregistruje – připomínka ale
 * zůstává v seznamu jako aktivní, dokud ji uživatel neoznačí Hotovo
 * (stejné chování jako iOS verze).
 */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = ReminderStore.get(context)
                val loadResult = store.reloadAndWait()
                if (loadResult == ReminderStore.ReloadResult.ERROR) {
                    Log.w("GeofenceReceiver", "Událost přeskočena – data připomínek se nepodařilo načíst")
                    return@launch
                }

                val reminders = store.reminders.value
                val scheduler = ReminderScheduler.get(context)

                if (event.hasError()) {
                    scheduler.handleGeofenceServiceError(event.errorCode, reminders)
                    Log.w("GeofenceReceiver", "Geofence service oznámila chybu code=${event.errorCode}")
                    return@launch
                }

                val transition = event.geofenceTransition
                val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
                if (ids.isEmpty()) return@launch

                var capacityChanged = false

                for (id in ids) {
                    val reminder = reminders.firstOrNull { it.id == id } ?: continue
                    if (reminder.isDone || reminder.kind != ReminderKind.LOCATION) continue

                    val matches =
                        (transition == Geofence.GEOFENCE_TRANSITION_ENTER
                                && reminder.trigger == TriggerType.ARRIVE) ||
                        (transition == Geofence.GEOFENCE_TRANSITION_EXIT
                                && reminder.trigger == TriggerType.LEAVE)
                    if (!matches) continue

                    NotificationHelper.show(context, reminder)

                    if (!reminder.repeats) {
                        // Jednorázová: zapamatovat „vystřeleno“. Následný reconcile
                        // ji vynechá a případný 101. reminder může obsadit uvolněný slot.
                        scheduler.markGeofenceFired(id)
                        capacityChanged = true
                    }
                }

                if (capacityChanged) {
                    scheduler.resyncGeofences(reminders)
                }
            } catch (e: Exception) {
                Log.w("GeofenceReceiver", "Chyba při zpracování geofence události", e)
            } finally {
                pending.finish()
            }
        }
    }
}
