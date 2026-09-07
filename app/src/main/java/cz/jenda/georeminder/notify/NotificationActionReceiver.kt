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
 * Obsluha tlačítek přímo na notifikaci.
 *
 * Každé zobrazení notifikace má jednorázový perzistentní token. První platná
 * akce token spotřebuje; dvojité klepnutí nebo souběh Hotovo/Snooze ze stejné
 * notifikace se proto nemůže aplikovat dvakrát ani po cold-startu procesu.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_ID) ?: return
        val action = intent.action ?: return
        val token = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_TOKEN) ?: return

        if (action !in SUPPORTED_ACTIONS) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = ReminderStore.get(context)
                val loadResult = store.reloadAndWait()
                if (loadResult == ReminderStore.ReloadResult.ERROR) {
                    Log.w("NotifActionReceiver", "Akce přeskočena – data připomínek se nepodařilo načíst")
                    return@launch
                }

                // Claim až po úspěšném načtení. Kdyby IO selhalo, token zůstane
                // platný a notifikace se nezruší jen kvůli přechodné chybě.
                val stateStore = SchedulerStateStore(context)
                if (!stateStore.consumeNotificationActionToken(id, token)) {
                    Log.i("NotifActionReceiver", "Duplicitní nebo zastaralá akce ignorována")
                    return@launch
                }

                val reminder = store.reminders.value.firstOrNull { it.id == id }
                if (reminder == null || reminder.isDone) {
                    NotificationHelper.cancel(context, id)
                    return@launch
                }

                when (action) {
                    NotificationHelper.ACTION_DONE -> store.markDone(reminder)
                    NotificationHelper.ACTION_SNOOZE ->
                        store.snooze(reminder, minutes = ReminderScheduler.SNOOZE_MINUTES)
                    NotificationHelper.ACTION_SNOOZE_MORNING ->
                        store.snoozeAt(reminder, nextNotificationMorningMillis())
                }
                NotificationHelper.cancel(context, id)
            } catch (e: Exception) {
                Log.w("NotifActionReceiver", "Chyba při obsluze tlačítka notifikace", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            NotificationHelper.ACTION_DONE,
            NotificationHelper.ACTION_SNOOZE,
            NotificationHelper.ACTION_SNOOZE_MORNING,
        )
    }
}

/** Nejbližší 8:00. Po osmé hodině je to zítřek, před osmou dnešní ráno. */
internal fun nextNotificationMorningMillis(now: Long = System.currentTimeMillis()): Long {
    return Calendar.getInstance().apply {
        timeInMillis = now
        if (get(Calendar.HOUR_OF_DAY) >= 8) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        set(Calendar.HOUR_OF_DAY, 8)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
