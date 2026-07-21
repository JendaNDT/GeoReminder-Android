package cz.jenda.georeminder.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Jednoduchá obálka nad Fused Location Providerem: kontrola oprávnění
 * a poslední známá poloha (pro vycentrování mapy a výpočet vzdáleností).
 * Zrcadlí LocationService z iOS verze.
 */
object LocationHolder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Poslední známá poloha uživatele (null = zatím neznámá). */
    val location = MutableStateFlow<Location?>(null)

    /**
     * true = poslední pokus o registraci geofence selhal (např. systémový limit
     * 100 geofence nebo vypnuté služby polohy). UI to ukáže bannerem, aby
     * hlídání místa neselhalo potichu.
     */
    val geofenceFailed = MutableStateFlow(false)

    fun hasFineLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** „Povolit vždy" – na Androidu 10+ zvláštní oprávnění, jinak stačí přesná poloha. */
    fun hasBackgroundLocation(context: Context): Boolean {
        if (!hasFineLocation(context)) return false
        if (Build.VERSION.SDK_INT < 29) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Jednorázově zjistí aktuální polohu (pokud je povolená). */
    fun refresh(context: Context) {
        if (!hasFineLocation(context)) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                val client = LocationServices.getFusedLocationProviderClient(appContext)
                val loc: Location? = client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                ).await()
                if (loc != null) {
                    location.value = loc
                } else {
                    // Záloha: poslední známá poloha ze systému
                    val last: Location? = client.lastLocation.await()
                    if (last != null) location.value = last
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
    }
}
