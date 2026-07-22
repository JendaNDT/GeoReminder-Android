package cz.jenda.georeminder.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import cz.jenda.georeminder.data.FeatureSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Přečte text připomínky nahlas přes systémový Text-to-Speech (zpracování je
 * lokální – nic neodchází ven). Volitelné, řídí se přepínačem
 * [FeatureSettings.readAloud].
 *
 * Volá se z receiverů (geofence / budík) uvnitř jejich korutiny: [speak] je
 * `suspend` a čeká na dořečení, takže systém proces neukončí uprostřed věty
 * (`goAsync` drží receiver naživu, dokud korutina neskončí).
 *
 * Pozn.: pro jednoduchost („prototyp" dle plánu) se engine vytvoří a po dořečení
 * zase zavře pro každou připomínku zvlášť. Sdílený engine by byl efektivnější,
 * ale komplikoval by lifecycle v receiverech – lze doladit později.
 */
object TtsSpeaker {

    // Strop čekání na dořečení. Držíme kvůli němu broadcast receiver naživu,
    // takže krátký (název připomínky se přečte za pár sekund) – ať nenarazíme
    // na časový limit doručení broadcastu.
    private const val MAX_WAIT_MS = 8_000L

    /**
     * Přečte [text], pokud je hlasité čtení zapnuté a telefon není v tichém ani
     * vibračním režimu. Čeká na dořečení (max ~8 s), pak se vrátí.
     */
    suspend fun speak(context: Context, text: String) {
        if (!FeatureSettings.readAloud.value) return
        if (text.isBlank()) return

        // Tichý / vibrační režim → nečíst (rozhodnuto ve Fázi 0.2).
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        withTimeoutOrNull(MAX_WAIT_MS) {
            speakBlocking(context.applicationContext, text)
        }
    }

    /** Sestaví text ke čtení: název, volitelně i tělo připomínky. */
    fun textFor(title: String, body: String): String {
        val t = title.trim()
        return if (FeatureSettings.readAloudFullText.value && body.isNotBlank()) {
            if (t.isEmpty()) body else "$t. $body"
        } else {
            t
        }
    }

    private suspend fun speakBlocking(appContext: Context, text: String) =
        suspendCancellableCoroutine<Unit> { cont ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(appContext) { status ->
                val tts = engine
                if (status != TextToSpeech.SUCCESS || tts == null) {
                    tts?.shutdown()
                    if (cont.isActive) cont.resume(Unit)
                    return@TextToSpeech
                }

                // Bez českého hlasu radši nečíst (ať to nezní "anglicky").
                val lang = tts.setLanguage(Locale("cs", "CZ"))
                if (lang == TextToSpeech.LANG_MISSING_DATA ||
                    lang == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    tts.shutdown()
                    if (cont.isActive) cont.resume(Unit)
                    return@TextToSpeech
                }

                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        tts.shutdown()
                        if (cont.isActive) cont.resume(Unit)
                    }
                    override fun onError(utteranceId: String?) {
                        tts.shutdown()
                        if (cont.isActive) cont.resume(Unit)
                    }
                })

                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "georeminder")
            }

            cont.invokeOnCancellation { engine?.shutdown() }
        }
}
