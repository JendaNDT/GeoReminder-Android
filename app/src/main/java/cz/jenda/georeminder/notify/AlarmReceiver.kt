package cz.jenda.georeminder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cz.jenda.georeminder.data.DiagnosticEventType
import cz.jenda.georeminder.data.DiagnosticStore
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
                val loadResult = store.reloadAndWait()
                if (loadResult == ReminderStore.ReloadResult.ERROR) {
                    Log.w("AlarmReceiver", "Doručení přeskočeno – data připomínek se nepodařilo načíst")
                    return@launch
                }

                val reminders = store.reminders.value
                val reminder = reminders.firstOrNull { it.id == id }
                    ?: return@launch
                if (reminder.isDone) return@launch

                val scheduler = ReminderScheduler.get(context)
                val isOneTime = !isSnooze && !isNag && reminder.timeRepeat == TimeRepeat.NEVER
                if (isOneTime && scheduler.isAlarmFired(id)) return@launch

                DiagnosticStore.get(context).record(
                    DiagnosticEventType.ALARM_FIRED,
                    detail = when {
                        isSnooze -> "snooze"
                        isNag -> "nag"
                        else -> reminder.timeRepeat.name
                    },
                )
                NotificationHelper.show(context, reminder)

                when {
                    isSnooze -> scheduler.resumeAfterSnooze(reminder, reminders)
                    isNag -> Unit
                    reminder.timeRepeat != TimeRepeat.NEVER ->
                        scheduler.scheduleNextOccurrence(reminder)
                    else -> scheduler.markAlarmFired(id)
                }
            } catch (e: Exception) {
                Log.w("AlarmReceiver", "Chyba při doručení připomínky", e)
            } finally {
                pending.finish()
            }
        }
    }
}
