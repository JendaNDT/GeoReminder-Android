package cz.jenda.georeminder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cz.jenda.georeminder.data.ReminderStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Obsluha tlačítek přímo na notifikaci:
 *  - Hotovo → označí připomínku jako hotovou (zruší hlídání)
 *  - Odložit o hodinu → nové připomenutí za 60 minut
 * Ekvivalent NotificationDelegate z iOS verze.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_ID) ?: return
        val action = intent.action ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = ReminderStore.get(context)
                val reminder = store.reloadAndGet().firstOrNull { it.id == id }
                    ?: return@launch

                when (action) {
                    NotificationHelper.ACTION_DONE -> {
                        store.markDoneAndWait(reminder)
                    }
                    NotificationHelper.ACTION_SNOOZE -> {
                        store.snooze(reminder, minutes = ReminderScheduler.SNOOZE_MINUTES)
                    }
                    NotificationHelper.ACTION_SNOOZE_MORNING -> {
                        // Nejbližší ráno v 8:00 (po půlnoci = ještě dnes)
                        val morning = Calendar.getInstance().apply {
                            if (get(Calendar.HOUR_OF_DAY) >= 8) {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                            set(Calendar.HOUR_OF_DAY, 8)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        store.snoozeAt(reminder, morning.timeInMillis)
                    }
                }
                NotificationHelper.cancel(context, id)
            } catch (e: Exception) {
                Log.w("NotifActionReceiver", "Chyba při obsluze tlačítka notifikace", e)
            } finally {
                pending.finish()
            }
        }
    }
}
