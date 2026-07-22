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

    fun show(context: Context, reminder: Reminder) {
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

        val wearableExtender = NotificationCompat.WearableExtender()
            .setHintHideIcon(false)

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
            .addAction(0, context.getString(R.string.action_done), doneIntent)
            .addAction(0, context.getString(R.string.action_snooze_hour), snoozeIntent)
            .addAction(0, context.getString(R.string.action_snooze_morning), morningIntent)
            .extend(wearableExtender)
            .setVibrate(longArrayOf(0, 150, 100, 150))

        if (FeatureSettings.get(context).groupByPlace && reminder.kind == ReminderKind.LOCATION && reminder.placeName.isNotBlank()) {
            val groupKey = "geo_place_${reminder.placeName.trim().lowercase()}"
            builder.setGroup(groupKey)
        }

        val notification = builder.build()

        // Naléhavé: zvuk se opakuje, dokud uživatel notifikaci nezavře
        if (reminder.alertStyle == AlertStyle.URGENT) {
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
        }

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
            TtsSpeaker.speakIfEnabled(context, reminder)
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

    fun cancel(context: Context, reminderId: String) {
        NotificationManagerCompat.from(context).cancel(reminderId.hashCode())
    }
}
