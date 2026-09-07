package cz.jenda.georeminder.model

import android.content.Context
import cz.jenda.georeminder.R

/** Lokalizované uživatelské texty odvozené z modelu Reminder. */
object ReminderText {
    fun kindLabel(context: Context, kind: ReminderKind): String = context.getString(
        when (kind) {
            ReminderKind.LOCATION -> R.string.kind_location
            ReminderKind.TIME -> R.string.kind_time
        }
    )

    fun triggerLabel(context: Context, trigger: TriggerType): String = context.getString(
        when (trigger) {
            TriggerType.ARRIVE -> R.string.trigger_arrive
            TriggerType.LEAVE -> R.string.trigger_leave
        }
    )

    fun triggerRepeatLabel(context: Context, trigger: TriggerType): String = context.getString(
        when (trigger) {
            TriggerType.ARRIVE -> R.string.trigger_arrive_repeat
            TriggerType.LEAVE -> R.string.trigger_leave_repeat
        }
    )

    fun timeRepeatLabel(context: Context, repeat: TimeRepeat): String = context.getString(
        when (repeat) {
            TimeRepeat.NEVER -> R.string.repeat_never
            TimeRepeat.DAILY -> R.string.repeat_daily
            TimeRepeat.WEEKLY -> R.string.repeat_weekly
        }
    )

    fun alertStyleLabel(context: Context, style: AlertStyle): String = context.getString(
        when (style) {
            AlertStyle.QUIET -> R.string.alert_quiet
            AlertStyle.DEFAULT -> R.string.alert_default
            AlertStyle.URGENT -> R.string.alert_urgent
        }
    )

    fun subtitle(context: Context, reminder: Reminder): String = when (reminder.kind) {
        ReminderKind.LOCATION -> buildString {
            append(
                context.getString(
                    R.string.reminder_location_subtitle,
                    reminder.placeName,
                    triggerLabel(context, reminder.trigger),
                )
            )
            if (reminder.repeats) append(context.getString(R.string.reminder_repeats_suffix))
        }

        ReminderKind.TIME -> {
            val due = reminder.dueDate ?: return context.getString(R.string.reminder_no_due_date)
            when (reminder.timeRepeat) {
                TimeRepeat.NEVER -> CzechFormat.dateTime(due)
                TimeRepeat.DAILY -> context.getString(
                    R.string.reminder_time_daily_subtitle,
                    CzechFormat.time(due),
                )
                TimeRepeat.WEEKLY -> context.getString(
                    R.string.reminder_time_weekly_subtitle,
                    CzechFormat.weeklyLabel(due, reminder.weekdays),
                )
            }
        }
    }
}
