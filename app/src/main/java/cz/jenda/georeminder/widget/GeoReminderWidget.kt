package cz.jenda.georeminder.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityIntent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import cz.jenda.georeminder.MainActivity
import cz.jenda.georeminder.R
import cz.jenda.georeminder.data.SharedStorage
import cz.jenda.georeminder.data.localizedSubtitle
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TriggerType

/**
 * Widget „Nejbližší připomínky" (Jetpack Glance) – ekvivalent WidgetKit widgetu.
 * 2 řádky na malé ploše, 3 na širší; obnovuje se při každém uložení dat
 * a sám každých ~30 minut.
 */
class GeoReminderWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val WIDE = DpSize(220.dp, 110.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val reminders = loadActive(context)
        // Plus v rohu widgetu → rovnou formulář nové připomínky
        val addIntent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra("shortcut_kind", "location")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        provideContent {
            WidgetContent(reminders, addIntent, context)
        }
    }

    /** Načte nejnovější aktivní připomínky ze sdíleného JSON souboru. */
    private fun loadActive(context: Context): List<Reminder> {
        val text = SharedStorage.readText(context, "reminders.json") ?: return emptyList()
        return SharedStorage.decodeReminders(text)
            .filter { !it.isDone }
            .sortedByDescending { it.createdAt }
            .take(3)
    }
}

class GeoReminderWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GeoReminderWidget()
}

private val widgetBackground = ColorProvider(
    day = Color(0xFFEFEFF4), night = Color(0xFF2C2C2E)
)
private val accentColor = ColorProvider(
    day = Color(0xFF007AFF), night = Color(0xFF0A84FF)
)
private val labelColor = ColorProvider(
    day = Color(0xFF000000), night = Color(0xFFFFFFFF)
)
private val secondaryColor = ColorProvider(
    day = Color(0x993C3C43), night = Color(0x99EBEBF5)
)

@Composable
private fun WidgetContent(reminders: List<Reminder>, addIntent: Intent, context: Context) {
    val size = LocalSize.current
    val limit = if (size.width >= 200.dp) 3 else 2

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp),
    ) {
        if (reminders.isEmpty()) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_check),
                    contentDescription = null,
                    modifier = GlanceModifier.size(22.dp),
                    colorFilter = ColorFilter.tint(secondaryColor),
                )
                Text(
                    text = context.getString(R.string.status_all_done),
                    style = TextStyle(color = secondaryColor, fontSize = 12.sp),
                )
            }
        } else {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(end = 20.dp)
            ) {
                reminders.take(limit).forEach { reminder ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            provider = ImageProvider(widgetIcon(reminder)),
                            contentDescription = null,
                            modifier = GlanceModifier.size(15.dp),
                            colorFilter = ColorFilter.tint(accentColor),
                        )
                        Spacer(GlanceModifier.width(7.dp))
                        Column {
                            Text(
                                text = reminder.title,
                                style = TextStyle(
                                    color = labelColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                maxLines = 1,
                            )
                            Text(
                                text = reminder.localizedSubtitle(context),
                                style = TextStyle(
                                    color = secondaryColor,
                                    fontSize = 11.sp,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // Plus v pravém horním rohu – nová připomínka na jeden ťuk
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_add),
                contentDescription = context.getString(R.string.widget_add_reminder),
                modifier = GlanceModifier
                    .size(18.dp)
                    .clickable(actionStartActivityIntent(addIntent)),
                colorFilter = ColorFilter.tint(accentColor),
            )
        }
    }
}

private fun widgetIcon(reminder: Reminder): Int = when {
    reminder.kind == ReminderKind.TIME -> R.drawable.ic_widget_clock
    reminder.trigger == TriggerType.LEAVE -> R.drawable.ic_widget_walk
    else -> R.drawable.ic_widget_pin
}
