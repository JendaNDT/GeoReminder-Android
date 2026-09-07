package cz.jenda.georeminder.model

import android.content.Context
import cz.jenda.georeminder.R
import cz.jenda.georeminder.data.LanguageController

/** Lokalizované uživatelské texty odvozené z modelu Reminder. */
object ReminderText {
    private fun strings(context: Context): Context = LanguageController.localizedContext(context)

    fun kindLabel(context: Context, kind: ReminderKind): String {
        val localized = strings(context)
        return localized.getString(
            when (kind) {
                ReminderKind.LOCATION -> R.string.kind_location
                ReminderKind.TIME -> R.string.kind_time
            }
        )
    }

    fun triggerLabel(context: Context, trigger: TriggerType): String {
        val localized = strings(context)
        return localized.getString(
            when (trigger) {
                TriggerType.ARRIVE -> R.string.trigger_arrive
                TriggerType.LEAVE -> R.string.trigger_leave
            }
        )
    }

    fun triggerRepeatLabel(context: Context, trigger: TriggerType): String {
        val localized = strings(context)
        return localized.getString(
            when (trigger) {
                TriggerType.ARRIVE -> R.string.trigger_arrive_repeat
                TriggerType.LEAVE -> R.string.trigger_leave_repeat
            }
        )
    }

    fun timeRepeatLabel(context: Context, repeat: TimeRepeat): String {
        val localized = strings(context)
        return localized.getString(
            when (repeat) {
                TimeRepeat.NEVER -> R.string.repeat_never
                TimeRepeat.DAILY -> R.string.repeat_daily
                TimeRepeat.WEEKLY -> R.string.repeat_weekly
            }
        )
    }

    fun alertStyleLabel(context: Context, style: AlertStyle): String {
        val localized = strings(context)
        return localized.getString(
            when (style) {
                AlertStyle.QUIET -> R.string.alert_quiet
                AlertStyle.DEFAULT -> R.string.alert_default
                AlertStyle.URGENT -> R.string.alert_urgent
            }
        )
    }

    fun subtitle(context: Context, reminder: Reminder): String {
        val localized = strings(context)
        val locale = LanguageController.localeForContext(localized)
        return when (reminder.kind) {
            ReminderKind.LOCATION -> buildString {
                append(
                    localized.getString(
                        R.string.reminder_location_subtitle,
                        reminder.placeName,
                        triggerLabel(localized, reminder.trigger),
                    )
                )
                if (reminder.repeats) {
                    append(localized.getString(R.string.reminder_repeats_suffix))
                }
            }

            ReminderKind.TIME -> {
                val due = reminder.dueDate
                    ?: return localized.getString(R.string.reminder_no_due_date)
                when (reminder.timeRepeat) {
                    TimeRepeat.NEVER -> CzechFormat.dateTimeForLocale(due, locale)
                    TimeRepeat.DAILY -> localized.getString(
                        R.string.reminder_time_daily_subtitle,
                        CzechFormat.timeForLocale(due, locale),
                    )
                    TimeRepeat.WEEKLY -> localized.getString(
                        R.string.reminder_time_weekly_subtitle,
                        CzechFormat.weeklyLabelForLocale(due, reminder.weekdays, locale),
                    )
                }
            }
        }
    }
}
