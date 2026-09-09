package cz.jenda.georeminder.notify

/**
 * Technický stav hlídání místa pro jednu připomínku.
 *
 * Stav neobsahuje souřadnice ani název místa. Je určený pouze pro spolehlivost,
 * diagnostiku a srozumitelné vysvětlení uživateli, proč reminder právě není hlídaný.
 */
enum class GeofenceRegistrationStatus {
    ACTIVE,
    FAILED_PERMISSION,
    FAILED_LOCATION_DISABLED,
    FAILED_TOO_MANY,
    FAILED_SERVICE,
    FAILED_INVALID_REGION,
    SNOOZED,
    FIRED;

    val isFailure: Boolean
        get() = this == FAILED_PERMISSION ||
            this == FAILED_LOCATION_DISABLED ||
            this == FAILED_TOO_MANY ||
            this == FAILED_SERVICE ||
            this == FAILED_INVALID_REGION
}

data class GeofenceRegistrationState(
    val status: GeofenceRegistrationStatus,
    val updatedAt: Long = System.currentTimeMillis(),
    /** Google Play Services status code, pokud selhání nějaký poskytlo. */
    val errorCode: Int? = null,
)
