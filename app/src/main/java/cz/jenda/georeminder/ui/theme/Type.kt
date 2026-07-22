package cz.jenda.georeminder.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cz.jenda.georeminder.R

/**
 * Písmo Inter – nejbližší volná náhrada SF Pro (DESIGN_SPEC §2).
 * Velikosti v sp odpovídají bodům z iOS specifikace.
 */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

object GeoType {
    /** 34 / Bold – velký titulek „GeoReminder" */
    val largeTitle = TextStyle(fontFamily = InterFamily, fontSize = 34.sp, fontWeight = FontWeight.Bold)

    /** 22 / Bold – titulky onboarding stránek */
    val title2Bold = TextStyle(fontFamily = InterFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold)

    /** 20 / SemiBold – titulek prázdného stavu */
    val emptyTitle = TextStyle(fontFamily = InterFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

    /** 17 / SemiBold – inline titulky, název místa, primární tlačítka */
    val headline = TextStyle(fontFamily = InterFamily, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

    /** 17 / Regular – běžný text */
    val body = TextStyle(fontFamily = InterFamily, fontSize = 17.sp, fontWeight = FontWeight.Normal)

    /** 15 / Regular – „Poloměr: 150 m", hlavičky sekcí */
    val subheadline = TextStyle(fontFamily = InterFamily, fontSize = 15.sp, fontWeight = FontWeight.Normal)

    /** 15 / Medium – text segmentovaného přepínače */
    val segment = TextStyle(fontFamily = InterFamily, fontSize = 15.sp, fontWeight = FontWeight.Medium)

    /** 13 / Regular – čipy, bannery, Přeskočit */
    val footnote = TextStyle(fontFamily = InterFamily, fontSize = 13.sp, fontWeight = FontWeight.Normal)

    /** 13 / SemiBold – tlačítko Nastavení v banneru */
    val footnoteBold = TextStyle(fontFamily = InterFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

    /** 12 / Regular – podtitulky řádků */
    val caption = TextStyle(fontFamily = InterFamily, fontSize = 12.sp, fontWeight = FontWeight.Normal)

    /** 12 / SemiBold – typové čipy (320 m, za 2 h, denně) */
    val chip = TextStyle(fontFamily = InterFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

    /** 11 / Regular – nápovědy pod sliderem */
    val caption2 = TextStyle(fontFamily = InterFamily, fontSize = 11.sp, fontWeight = FontWeight.Normal)
}


