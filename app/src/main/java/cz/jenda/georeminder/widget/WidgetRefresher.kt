package cz.jenda.georeminder.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Obnoví widget po každém uložení dat (ekvivalent WidgetCenter.reloadAllTimelines). */
object WidgetRefresher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                GeoReminderWidget().updateAll(appContext)
            } catch (_: Exception) {
            }
        }
    }
}
