package cz.jenda.georeminder.data

import android.content.Context
import androidx.annotation.StringRes
import cz.jenda.georeminder.R
import cz.jenda.georeminder.model.CzechFormat
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.model.TriggerType

@get:StringRes
val ReminderKind.labelRes: Int
    get() = if (this == ReminderKind.LOCATION) R.string.kind_location else R.string.kind_time

@get:StringRes
val TriggerType.labelRes: Int
    get() = if (this == TriggerType.ARRIVE) R.string.trigger_arrive else R.string.trigger_leave

@get:StringRes
val TriggerType.repeatLabelRes: Int
    get() = if (this == TriggerType.ARRIVE) {
        R.string.trigger_arrive_repeat
    } else {
        R.string.trigger_leave_repeat
    }

@get:StringRes
val TimeRepeat.labelRes: Int
    get() = when (this) {
        TimeRepeat.NEVER -> R.string.repeat_never
        TimeRepeat.DAILY -> R.string.repeat_daily
        TimeRepeat.WEEKLY -> R.string.repeat_weekly
    }

@get:StringRes
val cz.jenda.georeminder.model.AlertStyle.labelRes: Int
    get() = when (this) {
        cz.jenda.georeminder.model.AlertStyle.QUIET -> R.string.alert_quiet
        cz.jenda.georeminder.model.AlertStyle.DEFAULT -> R.string.alert_default
        cz.jenda.georeminder.model.AlertStyle.URGENT -> R.string.alert_urgent
    }

/** Uživatelské texty reminderu patří do resources, ne do serializovaného modelu. */
fun Reminder.localizedSubtitle(context: Context): String = when (kind) {
    ReminderKind.LOCATION -> buildString {
        append(placeName)
        append(" • ")
        append(
            context.getString(
                if (trigger == TriggerType.ARRIVE) R.string.trigger_arrive
                else R.string.trigger_leave
            )
        )
        if (repeats) {
            append(" • ")
            append(context.getString(R.string.subtitle_repeats))
        }
    }

    ReminderKind.TIME -> {
        val due = dueDate ?: return context.getString(R.string.subtitle_no_due)
        when (timeRepeat) {
            TimeRepeat.NEVER -> CzechFormat.dateTime(due)
            TimeRepeat.DAILY -> context.getString(
                R.string.subtitle_daily,
                CzechFormat.time(due)
            )
            TimeRepeat.WEEKLY -> context.getString(
                R.string.subtitle_weekly,
                CzechFormat.weeklyLabel(due, weekdays)
            )
        }
    }
}
