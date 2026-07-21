package cz.jenda.georeminder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

                // show() u dožadující se připomínky sám naplánuje další připomenutí
                NotificationHelper.show(context, reminder)

                if (!isSnooze && !isNag && reminder.timeRepeat != TimeRepeat.NEVER) {
                    ReminderScheduler(context).scheduleNextOccurrence(reminder)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
