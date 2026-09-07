package cz.jenda.georeminder.data

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Jedno místo pro kontroly a navigaci k systémovým „special app access“ volbám.
 * Samotná oprávnění vždy uděluje uživatel v Androidu; aplikace pouze vysvětluje
 * jejich význam a otevře správnou systémovou obrazovku.
 */
object SystemAccess {

    /** Android 12+ vyžaduje zvláštní přístup pro skutečně přesné AlarmManager alarmy. */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return alarmManager?.canScheduleExactAlarms() == true
    }

    /** Otevře systémovou obrazovku „Budíky a připomínky“ pro tuto aplikaci. */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .recoverCatching { openAppDetails(context) }
    }

    /** Lokalizovaný systémový název volby typu „Povolit vždy“. */
    fun backgroundLocationOptionLabel(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.backgroundPermissionOptionLabel.toString()
        } else {
            "Povolit vždy"
        }
    }

    /**
     * Android 11+ uděluje background location přes nastavení aplikace, nikoli
     * přes běžný runtime dialog. Zde uživatele pošleme na detail aplikace.
     */
    fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
