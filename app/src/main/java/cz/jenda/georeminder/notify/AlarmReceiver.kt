package cz.jenda.georeminder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.model.TimeRepeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Spuštění časové připomínky (nebo odloženého připomenutí).
 * U denního/týdenního opakování rovnou naplánuje další výskyt.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val isSnooze = intent.action == ReminderScheduler.ACTION_SNOOZE_FIRE
        val isNag = intent.action == ReminderScheduler.ACTION_NAG_FIRE

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = ReminderStore.get(context)
                store.reload()
                val reminder = store.reminders.value.firstOrNull { it.id == id }
                    ?: return@launch
                if (reminder.isDone) return@launch

                val scheduler = ReminderScheduler(context)
                val isOneTime = !isSnooze && !isNag && reminder.timeRepeat == TimeRepeat.NEVER
                // Jednorázovou připomínku už mohl doručit catch-up (po rebootu /
                // otevření appky) – pak ji přes budík nedoručovat podruhé.
                if (isOneTime && scheduler.isAlarmFired(id)) return@launch

                // show() u dožadující se připomínky sám naplánuje další připomenutí
                NotificationHelper.show(context, reminder)

                // Hlasité čtení (volitelné) – ne u dožadování, ať se to neopakuje
                // dokola každých 5 minut.
                if (!isNag) {
                    TtsSpeaker.speak(
                        context,
                        TtsSpeaker.textFor(reminder.title, NotificationHelper.body(reminder)),
                    )
                }

                when {
                    isSnooze -> {
                        // Odložení doručeno – zapomenout uloženou značku odložení.
                        scheduler.clearSnooze(id)
                    }
                    isNag -> {
                        // Dožadování: show() si další připomenutí naplánovalo samo.
                    }
                    reminder.timeRepeat != TimeRepeat.NEVER -> {
                        scheduler.scheduleNextOccurrence(reminder)
                    }
                    else -> {
                        // Jednorázový budík se odpálil – ať ho catch-up po restartu
                        // telefonu neposlal znovu.
                        scheduler.markAlarmFired(id)
                    }
                }
            } catch (e: Exception) {
                Log.w("AlarmReceiver", "Chyba při doručení připomínky", e)
            } finally {
                pending.finish()
            }
        }
    }
}
