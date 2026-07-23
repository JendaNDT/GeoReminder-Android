package cz.jenda.georeminder.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal val widgetRevisionKey = longPreferencesKey("widget_revision")

/** Obnoví widget po každém uložení dat (ekvivalent WidgetCenter.reloadAllTimelines). */
object WidgetRefresher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val widget = GeoReminderWidget()
                GlanceAppWidgetManager(appContext)
                    .getGlanceIds(GeoReminderWidget::class.java)
                    .forEach { id ->
                        updateAppWidgetState(appContext, id) { preferences ->
                            preferences[widgetRevisionKey] =
                                (preferences[widgetRevisionKey] ?: 0L) + 1L
                        }
                        widget.update(appContext, id)
                    }
            } catch (error: Exception) {
                Log.w("WidgetRefresher", "Obnovení widgetu selhalo", error)
            }
        }
    }
}
