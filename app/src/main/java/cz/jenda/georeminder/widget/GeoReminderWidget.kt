package cz.jenda.georeminder.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.currentState
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
import androidx.glance.state.PreferencesGlanceStateDefinition
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
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Plus v rohu widgetu → rovnou formulář nové připomínky
        val addIntent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra("shortcut_kind", "location")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        provideContent {
            // Čtení revize naváže kompozici na stav widgetu. Její zvýšení po
            // každém uložení zaručí nové načtení i v dlouho běžící Glance relaci.
            currentState<Preferences>()[widgetRevisionKey]
            WidgetContent(loadActive(context), addIntent, context)
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
    val isWide = size.width >= 200.dp
    val limit = if (isWide) 3 else 2
    // Testovaný One UI launcher zmenšuje RemoteViews širokého widgetu přibližně
    // na 83 %. Větší zdrojové rozměry udrží typografii čitelnou a dotykovou
    // plochu tlačítka alespoň na doporučených 48 dp i po tomto škálování.
    val emptyIconSize = if (isWide) 40.dp else 28.dp
    val emptyTextSize = if (isWide) 18.sp else 14.sp
    val reminderIconSize = if (isWide) 26.dp else 20.dp
    val reminderIconGap = if (isWide) 12.dp else 9.dp
    val titleTextSize = if (isWide) 18.sp else 15.sp
    val subtitleTextSize = if (isWide) 16.sp else 13.sp
    val rowBottomPadding = if (isWide) 12.dp else 8.dp
    val addButtonSize = if (isWide) 58.dp else 36.dp
    val addIconSize = if (isWide) 32.dp else 24.dp
    val firstRowEndPadding = if (isWide) 64.dp else 38.dp

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
                    modifier = GlanceModifier.size(emptyIconSize),
                    colorFilter = ColorFilter.tint(secondaryColor),
                )
                Text(
                    text = context.getString(R.string.status_all_done),
                    style = TextStyle(color = secondaryColor, fontSize = emptyTextSize),
                )
            }
        } else {
            Column(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                reminders.take(limit).forEachIndexed { index, reminder ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(
                                end = if (index == 0) firstRowEndPadding else 0.dp,
                                bottom = rowBottomPadding,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            provider = ImageProvider(widgetIcon(reminder)),
                            contentDescription = null,
                            modifier = GlanceModifier.size(reminderIconSize),
                            colorFilter = ColorFilter.tint(accentColor),
                        )
                        Spacer(GlanceModifier.width(reminderIconGap))
                        Column {
                            Text(
                                text = reminder.title,
                                style = TextStyle(
                                    color = labelColor,
                                    fontSize = titleTextSize,
                                    fontWeight = FontWeight.Bold,
                                ),
                                maxLines = 1,
                            )
                            Text(
                                text = reminder.localizedSubtitle(context),
                                style = TextStyle(
                                    color = secondaryColor,
                                    fontSize = subtitleTextSize,
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
            Box(
                modifier = GlanceModifier
                    .size(addButtonSize)
                    .clickable(actionStartActivityIntent(addIntent)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_add),
                    contentDescription = context.getString(R.string.widget_add_reminder),
                    modifier = GlanceModifier.size(addIconSize),
                    colorFilter = ColorFilter.tint(accentColor),
                )
            }
        }
    }
}

private fun widgetIcon(reminder: Reminder): Int = when {
    reminder.kind == ReminderKind.TIME -> R.drawable.ic_widget_clock
    reminder.trigger == TriggerType.LEAVE -> R.drawable.ic_widget_walk
    else -> R.drawable.ic_widget_pin
}
