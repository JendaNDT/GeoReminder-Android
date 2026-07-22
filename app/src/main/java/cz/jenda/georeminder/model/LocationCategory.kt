package cz.jenda.georeminder.model

import kotlinx.serialization.Serializable

/**
 * Skupina míst (kategorie) pro hromadné připomínky (např. Supermarkety, Lékárny, Obchody).
 */
@Serializable
data class LocationCategory(
    val id: String = newUUID(),
    val name: String = "",
    val iconName: String = "store",
    val placeIds: List<String> = emptyList(),
)
