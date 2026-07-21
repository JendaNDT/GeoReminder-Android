package cz.jenda.georeminder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cz.jenda.georeminder.data.ReminderStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Po restartu telefonu (nebo aktualizaci appky) Android zahodí všechny
 * geofence i budíky – tady je znovu zaregistrujeme. iOS tohle řeší sám,
 * na Androidu je to naše práce.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = ReminderStore.get(context)
                store.reload()
                store.resyncAll()
            } catch (e: Exception) {
                Log.w("BootReceiver", "Chyba při obnově připomínek po restartu", e)
            } finally {
                pending.finish()
            }
        }
    }
}
