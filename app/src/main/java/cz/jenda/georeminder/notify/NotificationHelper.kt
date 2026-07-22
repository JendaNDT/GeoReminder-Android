package cz.jenda.georeminder.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cz.jenda.georeminder.MainActivity
import cz.jenda.georeminder.R
import cz.jenda.georeminder.model.AlertStyle
import cz.jenda.georeminder.model.CzechFormat
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.model.TriggerType

/**
 * Stavba a zobrazování notifikací s tlačítky „Hotovo" a „Odložit o hodinu".
 * Kanál má IMPORTANCE_HIGH, takže se banner ukáže i při běžící appce
 * (ekvivalent iOS zobrazení v popředí).
 */
object NotificationHelper {
    const val CHANNEL_ID = "reminders"
    const val CHANNEL_QUIET_ID = "reminders_quiet"
    const val CHANNEL_URGENT_ID = "reminders_urgent"

    const val ACTION_DONE = "cz.jenda.georeminder.ACTION_DONE"
    const val ACTION_SNOOZE = "cz.jenda.georeminder.ACTION_SNOOZE"
    const val ACTION_SNOOZE_MORNING = "cz.jenda.georeminder.ACTION_SNOOZE_MORNING"
    // Sdílíme jednu hodnotu se schedulerem, ať se doručování nerozejde.
    const val EXTRA_REMINDER_ID = ReminderScheduler.EXTRA_REMINDER_ID

    /** Skupina notifikací pro připomínky spuštěné na jednom místě. */
    const val GROUP_KEY_PLACE = "cz.jenda.georeminder.group.PLACE"
    private const val GROUP_SUMMARY_ID = 0x67726F75 // stabilní ID souhrnné notifikace

    // Reminder ID aktuálně zobrazené ve skupině „místo" – aby je i opakované
    // připomenutí (nag) znovu ukázalo ve skupině, ne samostatně.
    private val groupedIds = java.util.Collections.synchronizedSet(HashSet<String>())

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // Výchozí: banner + běžný zvuk
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Připomínky",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Připomínky na místa a časy"
            }
        )

        // Tiché: jen v liště, žádný zvuk ani vyskakování
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_QUIET_ID,
                "Tiché připomínky",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Připomínky bez zvuku"
                setSound(null, null)
                enableVibration(false)
            }
        )

        // Naléhavé: budíkový zvuk (hraje na hlasitost budíku) + silná vibrace
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_URGENT_ID,
                "Naléhavé připomínky",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Důležité připomínky s hlasitým zvukem"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600)
            }
        )
    }

    private fun channelFor(style: AlertStyle): String = when (style) {
        AlertStyle.QUIET -> CHANNEL_QUIET_ID
        AlertStyle.DEFAULT -> CHANNEL_ID
        AlertStyle.URGENT -> CHANNEL_URGENT_ID
    }

    /** Tělo notifikace – texty 1:1 podle iOS verze. */
    fun body(reminder: Reminder): String = when (reminder.kind) {
        ReminderKind.LOCATION ->
            if (reminder.trigger == TriggerType.ARRIVE) {
                "Jsi u místa: ${reminder.placeName}"
            } else {
                "Odjíždíš od místa: ${reminder.placeName}"
            }
        ReminderKind.TIME -> {
            val due = reminder.dueDate
            if (due == null) "" else when (reminder.timeRepeat) {
                TimeRepeat.NEVER -> "Připomínka na " + CzechFormat.dateTime(due)
                TimeRepeat.DAILY -> "Opakuje se každý den v " + CzechFormat.time(due)
                TimeRepeat.WEEKLY -> "Opakuje se každý týden: " +
                        CzechFormat.weeklyLabel(due, reminder.weekdays)
            }
        }
    }

    fun show(context: Context, reminder: Reminder, group: String? = null) {
        val notifId = reminder.id.hashCode()

        val contentIntent = PendingIntent.getActivity(
            context,
            notifId,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_DONE)
                .putExtra(EXTRA_REMINDER_ID, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            notifId + 2,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_REMINDER_ID, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val morningIntent = PendingIntent.getBroadcast(
            context,
            notifId + 3,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_SNOOZE_MORNING)
                .putExtra(EXTRA_REMINDER_ID, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelFor(reminder.alertStyle))
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle(reminder.title)
            .setContentText(body(reminder))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body(reminder)))
            .setPriority(
                if (reminder.alertStyle == AlertStyle.QUIET) {
                    NotificationCompat.PRIORITY_LOW
                } else {
                    NotificationCompat.PRIORITY_HIGH
                }
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        // Připomínka na místě: první akce „Navigovat" – otevře mapy s navigací
        // na daný bod. Míří přes PendingIntent.getActivity na NavigateActivity
        // (ne přes receiver – Android 12+ by start aktivity z receiveru zablokoval).
        // Systémový intent, nepoužívá Maps API klíč appky.
        if (reminder.kind == ReminderKind.LOCATION) {
            val navigateIntent = PendingIntent.getActivity(
                context,
                notifId + 4,
                Intent(context, NavigateActivity::class.java).apply {
                    putExtra(NavigateActivity.EXTRA_LAT, reminder.latitude)
                    putExtra(NavigateActivity.EXTRA_LNG, reminder.longitude)
                    putExtra(NavigateActivity.EXTRA_NAME, reminder.placeName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Navigovat", navigateIntent)
        }

        builder.addAction(0, "Hotovo", doneIntent)
            .addAction(0, "Odložit o hodinu", snoozeIntent)

        // „Zítra ráno" jen u časových připomínek – u míst by to bylo čtvrté tlačítko
        // a systém zobrazí max 3 (u míst má přednost Navigovat).
        if (reminder.kind != ReminderKind.LOCATION) {
            builder.addAction(0, "Zítra ráno", morningIntent)
        }

        // Zařazení do skupiny (chytré seskupení notifikací na stejném místě).
        // I opakované připomenutí (nag) drží skupinu, dokud je připomínka grouped.
        val effectiveGroup = group ?: if (reminder.id in groupedIds) GROUP_KEY_PLACE else null
        if (effectiveGroup != null) {
            builder.setGroup(effectiveGroup)
        }

        val notification = builder.build()

        // Naléhavé: zvuk se opakuje, dokud uživatel notifikaci nezavře
        if (reminder.alertStyle == AlertStyle.URGENT) {
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
        }

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
            // Uživatel nepovolil notifikace – appka to ukazuje oranžovým bannerem.
        }

        // Dožadování: nepotvrzená připomínka se za 5 minut připomene znovu.
        // Neplánovat, když jsou notifikace vypnuté celé NEBO jen tento kanál –
        // jinak by neviditelná smyčka budíků běžela donekonečna.
        val channelBlocked = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(channelFor(reminder.alertStyle))
            ?.importance == NotificationManager.IMPORTANCE_NONE
        if (reminder.nagging && !reminder.isDone && !channelBlocked &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            ReminderScheduler(context).scheduleNag(reminder)
        }
    }

    /**
     * Zobrazí víc připomínek spuštěných na jednom místě jako skupinu: každou
     * jako samostatnou notifikaci (vlastní tlačítka i stav – splnění jedné
     * neukončí ostatní) plus jedno souhrnné upozornění „Na tomto místě máš N…".
     * Systém sbalí skupinu do souhrnu, po rozbalení ukáže jednotlivé.
     */
    fun showGroup(context: Context, reminders: List<Reminder>) {
        if (reminders.isEmpty()) return
        if (reminders.size == 1) {
            show(context, reminders[0])
            return
        }

        // Jednotlivé připomínky (zařazené do skupiny)
        reminders.forEach {
            groupedIds.add(it.id)
            show(context, it, group = GROUP_KEY_PLACE)
        }

        // Souhrn skupiny
        val title = "Na tomto místě máš " + pluralReminders(reminders.size)
        val inbox = NotificationCompat.InboxStyle().setSummaryText(title)
        reminders.forEach { inbox.addLine(it.title.ifBlank { "Připomínka" }) }

        val contentIntent = PendingIntent.getActivity(
            context,
            GROUP_SUMMARY_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Souhrn vede na kanál podle stylu dětí – skupina samých „Tichých"
        // připomínek nemá dělat hlasitý souhrn.
        val allQuiet = reminders.all { it.alertStyle == AlertStyle.QUIET }
        val summary = NotificationCompat.Builder(
            context, if (allQuiet) CHANNEL_QUIET_ID else CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle(title)
            .setContentText("Klepnutím otevřeš přehled.")
            .setStyle(inbox)
            .setPriority(
                if (allQuiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH,
            )
            .setGroup(GROUP_KEY_PLACE)
            .setGroupSummary(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(GROUP_SUMMARY_ID, summary)
        } catch (_: SecurityException) {
            // Notifikace nepovolené – appka to ukazuje bannerem.
        }
    }

    /** České skloňování: „2 připomínky" (2–4) / „5 připomínek" (5+). */
    private fun pluralReminders(n: Int): String = if (n >= 5) "$n připomínek" else "$n připomínky"

    fun cancel(context: Context, reminderId: String) {
        val notifId = reminderId.hashCode()
        groupedIds.remove(reminderId)
        NotificationManagerCompat.from(context).cancel(notifId)
        cleanupGroupSummary(context, notifId)
    }

    /**
     * Zruší souhrn skupiny „místo", pokud pod ním po zrušení dětské notifikace
     * nezůstala žádná připomínka (jinak by osiřelý souhrn visel v liště).
     * Právě zrušené ID se vylučuje – systém ho z [NotificationManager.getActiveNotifications]
     * odstraní asynchronně, takže by tam ještě chvíli figurovalo.
     */
    private fun cleanupGroupSummary(context: Context, justCancelledId: Int) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            val hasChild = nm.activeNotifications.any {
                it.id != GROUP_SUMMARY_ID &&
                    it.id != justCancelledId &&
                    it.notification.group == GROUP_KEY_PLACE
            }
            if (!hasChild) {
                NotificationManagerCompat.from(context).cancel(GROUP_SUMMARY_ID)
            }
        } catch (_: Exception) {
            // getActiveNotifications může vzácně vyhodit – pak souhrn nechat být.
        }
    }
}
