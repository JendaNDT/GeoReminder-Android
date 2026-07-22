package cz.jenda.georeminder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationServices
import cz.jenda.georeminder.data.FeatureSettings
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.model.Reminder
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
        if (event.hasError()) return

        val transition = event.geofenceTransition
        val ids = event.triggeringGeofences?.map { it.requestId } ?: return
        if (ids.isEmpty()) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = ReminderStore.get(context)
                store.reload()
                val reminders = store.reminders.value

                val fired = mutableListOf<Reminder>()

                for (id in ids) {
                    val reminder = reminders.firstOrNull { it.id == id } ?: continue
                    if (reminder.isDone || reminder.kind != ReminderKind.LOCATION) continue

                    val matches =
                        (transition == Geofence.GEOFENCE_TRANSITION_ENTER
                                && reminder.trigger == TriggerType.ARRIVE) ||
                        (transition == Geofence.GEOFENCE_TRANSITION_EXIT
                                && reminder.trigger == TriggerType.LEAVE)
                    if (!matches) continue

                    fired.add(reminder)

                    if (!reminder.repeats) {
                        // Jednorázová: geofence už nehlídat (notifikace „vystřelila")
                        // a zapamatovat si to, aby ji resync znovu nezaregistroval
                        ReminderScheduler(context).markGeofenceFired(id)
                        LocationServices.getGeofencingClient(context)
                            .removeGeofences(listOf(id))
                    }
                }

                // Zobrazení: když je zapnuté seskupení a spustilo se víc připomínek
                // najednou (jedna geofence událost = stejné místo), ukázat je jako
                // jedno souhrnné upozornění; jinak každou zvlášť (jako dosud).
                if (FeatureSettings.groupByPlace.value && fired.size >= 2) {
                    NotificationHelper.showGroup(context, fired)
                } else {
                    fired.forEach { NotificationHelper.show(context, it) }
                }

                // Hlasité čtení (volitelné). Když se najednou spustí víc připomínek
                // na jednom místě, přečti jejich názvy za sebou.
                if (fired.isNotEmpty()) {
                    val text = if (fired.size == 1) {
                        TtsSpeaker.textFor(fired[0].title, NotificationHelper.body(fired[0]))
                    } else {
                        fired.joinToString(", ") { it.title }
                    }
                    TtsSpeaker.speak(context, text)
                }
            } catch (e: Exception) {
                Log.w("GeofenceReceiver", "Chyba při zpracování geofence události", e)
            } finally {
                pending.finish()
            }
        }
    }
}
