package cz.jenda.georeminder.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Locale
import java.util.UUID

/** Výchozí poloměr geo-oblasti (metry). */
const val DEFAULT_RADIUS = 150.0

/**
 * Datumy se ukládají stejně jako v iOS verzi: jako počet sekund od 1. 1. 2001
 * (Apple "reference date"). Díky tomu jsou JSON soubory obou platforem
 * vzájemně čitelné (budoucí export/import).
 */
object AppleDateSerializer : KSerializer<Long> {
    private const val APPLE_EPOCH_OFFSET_SECONDS = 978_307_200.0

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AppleDate", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeDouble(value / 1000.0 - APPLE_EPOCH_OFFSET_SECONDS)
    }

    override fun deserialize(decoder: Decoder): Long {
        return ((decoder.decodeDouble() + APPLE_EPOCH_OFFSET_SECONDS) * 1000.0).toLong()
    }
}

fun newUUID(): String = UUID.randomUUID().toString().uppercase(Locale.ROOT)

/** Typ spouštěče připomínky – při příjezdu na místo, nebo při odjezdu z něj. */
@Serializable
enum class TriggerType {
    @SerialName("arrive") ARRIVE,
    @SerialName("leave") LEAVE;

    val label: String
        get() = when (this) {
            ARRIVE -> "Když přijedu"
            LEAVE -> "Když odjedu"
        }

    val repeatLabel: String
        get() = when (this) {
            ARRIVE -> "Opakovat při každém příjezdu"
            LEAVE -> "Opakovat při každém odjezdu"
        }
}

/** Druh připomínky – na místo, nebo na čas. */
@Serializable
enum class ReminderKind {
    @SerialName("location") LOCATION,
    @SerialName("time") TIME;

    val label: String
        get() = when (this) {
            LOCATION -> "Na místě"
            TIME -> "Na čas"
        }
}

/**
 * Druh upozornění (rozšíření Android verze – iOS pole ignoruje).
 * Tiché = bez zvuku, Naléhavé = budíkový zvuk hrající do zavření notifikace.
 */
@Serializable
enum class AlertStyle {
    @SerialName("quiet") QUIET,
    @SerialName("default") DEFAULT,
    @SerialName("urgent") URGENT;

    val label: String
        get() = when (this) {
            QUIET -> "Tiché"
            DEFAULT -> "Výchozí"
            URGENT -> "Naléhavé"
        }
}

/** Opakování časové připomínky. */
@Serializable
enum class TimeRepeat {
    @SerialName("never") NEVER,
    @SerialName("daily") DAILY,
    @SerialName("weekly") WEEKLY;

    val label: String
        get() = when (this) {
            NEVER -> "Neopakovat"
            DAILY -> "Každý den"
            WEEKLY -> "Každý týden"
        }
}

/** Jedna připomínka (geolokační nebo časová). Stejná pole jako v iOS verzi. */
@Serializable
data class Reminder(
    val id: String = newUUID(),
    val title: String = "",
    val kind: ReminderKind = ReminderKind.LOCATION,

    // Pole pro kind == LOCATION
    val placeName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Double = DEFAULT_RADIUS,
    val trigger: TriggerType = TriggerType.ARRIVE,
    val repeats: Boolean = false,

    // Pole pro kind == TIME
    @Serializable(with = AppleDateSerializer::class)
    val dueDate: Long? = null,
    val timeRepeat: TimeRepeat = TimeRepeat.NEVER,
    /**
     * Vybrané dny v týdnu pro týdenní opakování (ISO: 1=pondělí … 7=neděle).
     * null / jeden den = chování jako iOS verze (den podle dueDate).
     * Rozšíření Android verze – iOS toto pole při čtení ignoruje.
     */
    val weekdays: List<Int>? = null,

    val isDone: Boolean = false,
    @Serializable(with = AppleDateSerializer::class)
    val createdAt: Long = System.currentTimeMillis(),

    // Rozšíření Android verze (iOS tato pole při čtení ignoruje)
    /** Druh upozornění: tiché / výchozí / naléhavé. */
    val alertStyle: AlertStyle = AlertStyle.DEFAULT,
    /** Dožadování: nepotvrzená notifikace se vrací každých 5 minut. */
    val nagging: Boolean = false,

    /**
     * Přílohy připomínky (foto, PDF, QR obrázek…). Soubory jsou ZKOPÍROVANÉ
     * v privátním úložišti aplikace (`filesDir/attachments/`), takže přežijí
     * smazání originálu i přeinstalaci (přes řízenou zálohu).
     * Rozšíření Android verze – iOS pole při čtení ignoruje.
     */
    val attachments: List<Attachment> = emptyList(),
) {
    /** Popisek do seznamu (druhý řádek) – bez vzdálenosti. */
    val subtitle: String
        get() = when (kind) {
            ReminderKind.LOCATION -> buildString {
                append(placeName)
                append(" • ")
                append(trigger.label)
                if (repeats) append(" • opakuje se")
            }
            ReminderKind.TIME -> {
                val due = dueDate ?: return "Bez termínu"
                when (timeRepeat) {
                    TimeRepeat.NEVER -> CzechFormat.dateTime(due)
                    TimeRepeat.DAILY -> CzechFormat.time(due) + " • každý den"
                    TimeRepeat.WEEKLY -> CzechFormat.weeklyLabel(due, weekdays) + " • každý týden"
                }
            }
        }
}

/** Druh přílohy – řídí ikonu v seznamu. */
@Serializable
enum class AttachmentKind {
    @SerialName("photo") PHOTO,
    @SerialName("document") DOCUMENT,
    @SerialName("other") OTHER;
}

/**
 * Jedna příloha připomínky. Soubor je zkopírovaný v `filesDir/attachments/`
 * pod názvem [fileName]; [displayName] je původní název souboru pro zobrazení.
 * Rozšíření Android verze – iOS ignoruje.
 */
@Serializable
data class Attachment(
    val id: String = newUUID(),
    val fileName: String = "",
    val displayName: String = "",
    val mime: String = "",
    val kind: AttachmentKind = AttachmentKind.OTHER,
)

/** Oblíbené místo (Domov, Práce, Obchod…) pro rychlé zadávání připomínek. */
@Serializable
data class FavoritePlace(
    val id: String = newUUID(),
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Double = DEFAULT_RADIUS,
)
