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
import cz.jenda.georeminder.data.FeatureSettings
import cz.jenda.georeminder.data.LanguageController
import cz.jenda.georeminder.model.AlertStyle
import cz.jenda.georeminder.model.CzechFormat
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.model.TriggerType
import java.util.UUID

/**
 * Stavba a zobrazování notifikací s tlačítky „Hotovo" a „Odložit".
 * Každé zobrazení dostává vlastní akční token, takže první interakce atomicky
 * vyhraje a dvojité/souběžné interakce ze stejné notifikace se ignorují.
 */
object NotificationHelper {
    const val CHANNEL_ID = "reminders"
    const val CHANNEL_QUIET_ID = "reminders_quiet"
    const val CHANNEL_URGENT_ID = "reminders_urgent"

    const val ACTION_OPEN_REMINDER = "cz.jenda.georeminder.ACTION_OPEN_REMINDER"
    const val ACTION_DONE = "cz.jenda.georeminder.ACTION_DONE"
    const val ACTION_SNOOZE = "cz.jenda.georeminder.ACTION_SNOOZE"
    const val ACTION_SNOOZE_MORNING = "cz.jenda.georeminder.ACTION_SNOOZE_MORNING"
    const val EXTRA_REMINDER_ID = ReminderScheduler.EXTRA_REMINDER_ID
    const val EXTRA_NOTIFICATION_TOKEN = "notification_action_token"

    fun createChannel(context: Context) {
        val strings = LanguageController.localizedContext(context)
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                strings.getString(R.string.notification_channel_default),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = strings.getString(R.string.notification_channel_default_desc)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_QUIET_ID,
                strings.getString(R.string.notification_channel_quiet),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = strings.getString(R.string.notification_channel_quiet_desc)
                setSound(null, null)
                enableVibration(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_URGENT_ID,
                strings.getString(R.string.notification_channel_urgent),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = strings.getString(R.string.notification_channel_urgent_desc)
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

    fun body(context: Context, reminder: Reminder): String {
        val strings = LanguageController.localizedContext(context)
        val locale = LanguageController.localeForContext(strings)
        return when (reminder.kind) {
            ReminderKind.LOCATION -> if (reminder.trigger == TriggerType.ARRIVE) {
                strings.getString(R.string.notification_arrive_body, reminder.placeName)
            } else {
                strings.getString(R.string.notification_leave_body, reminder.placeName)
            }

            ReminderKind.TIME -> {
                val due = reminder.dueDate
                if (due == null) "" else when (reminder.timeRepeat) {
                    TimeRepeat.NEVER -> strings.getString(
                        R.string.notification_time_once_body,
                        CzechFormat.dateTimeForLocale(due, locale),
                    )
                    TimeRepeat.DAILY -> strings.getString(
                        R.string.notification_time_daily_body,
                        CzechFormat.timeForLocale(due, locale),
                    )
                    TimeRepeat.WEEKLY -> strings.getString(
                        R.string.notification_time_weekly_body,
                        CzechFormat.weeklyLabelForLocale(due, reminder.weekdays, locale),
                    )
                }
            }
        }
    }

    fun show(context: Context, reminder: Reminder) {
        val strings = LanguageController.localizedContext(context)
        val stateStore = SchedulerStateStore(context)
        val notifId = stateStore.requestCode(
            reminder.id,
            SchedulerStateStore.OFFSET_NOTIFICATION_CONTENT,
        )
        val actionToken = UUID.randomUUID().toString()
        stateStore.setNotificationActionToken(reminder.id, actionToken)

        val contentIntent = PendingIntent.getActivity(
            context,
            notifId,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_REMINDER)
                .putExtra(EXTRA_REMINDER_ID, reminder.id)
                .putExtra(EXTRA_NOTIFICATION_TOKEN, actionToken)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = PendingIntent.getBroadcast(
            context,
            stateStore.requestCode(reminder.id, SchedulerStateStore.OFFSET_NOTIFICATION_DONE),
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_DONE)
                .putExtra(EXTRA_REMINDER_ID, reminder.id)
                .putExtra(EXTRA_NOTIFICATION_TOKEN, actionToken),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            stateStore.requestCode(reminder.id, SchedulerStateStore.OFFSET_NOTIFICATION_SNOOZE),
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_REMINDER_ID, reminder.id)
                .putExtra(EXTRA_NOTIFICATION_TOKEN, actionToken),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val morningIntent = PendingIntent.getBroadcast(
            context,
            stateStore.requestCode(reminder.id, SchedulerStateStore.OFFSET_NOTIFICATION_MORNING),
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_SNOOZE_MORNING)
                .putExtra(EXTRA_REMINDER_ID, reminder.id)
                .putExtra(EXTRA_NOTIFICATION_TOKEN, actionToken),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wearableExtender = NotificationCompat.WearableExtender()
            .setHintHideIcon(false)
        val body = body(strings, reminder)

        val builder = NotificationCompat.Builder(context, channelFor(reminder.alertStyle))
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle(reminder.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
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
            .addAction(0, strings.getString(R.string.action_done), doneIntent)
            .addAction(0, strings.getString(R.string.action_snooze_hour), snoozeIntent)
            .addAction(0, strings.getString(R.string.action_snooze_morning), morningIntent)
            .extend(wearableExtender)
            .setVibrate(longArrayOf(0, 150, 100, 150))

        if (FeatureSettings.groupByPlace.value && reminder.kind == ReminderKind.LOCATION && reminder.placeName.isNotBlank()) {
            val groupKey = "geo_place_${reminder.placeName.trim().lowercase()}"
            builder.setGroup(groupKey)
        }

        val notification = builder.build()

        if (reminder.alertStyle == AlertStyle.URGENT) {
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
        }

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
            TtsSpeaker.speakIfEnabled(context, reminder)
        } catch (_: SecurityException) {
            // Uživatel nepovolil notifikace – appka to ukazuje oranžovým bannerem.
        }

        val channelBlocked = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(channelFor(reminder.alertStyle))
            ?.importance == NotificationManager.IMPORTANCE_NONE
        if (reminder.nagging && !reminder.isDone && !channelBlocked &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            ReminderScheduler.get(context).scheduleNag(reminder)
        }
    }

    /** První interakce s konkrétním zobrazením notifikace vyhraje. */
    fun consumeInteractionToken(context: Context, reminderId: String, token: String): Boolean =
        SchedulerStateStore(context).consumeNotificationActionToken(reminderId, token)

    fun cancel(context: Context, reminderId: String) {
        val stateStore = SchedulerStateStore(context)
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(
            stateStore.requestCode(reminderId, SchedulerStateStore.OFFSET_NOTIFICATION_CONTENT)
        )
        // Zrušit i notifikaci vytvořenou verzí <= 2.7, která používala hash ID.
        manager.cancel(reminderId.hashCode())
        stateStore.clearNotificationActionToken(reminderId)
    }
}
