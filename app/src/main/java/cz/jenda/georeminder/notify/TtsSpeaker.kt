package cz.jenda.georeminder.notify

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import cz.jenda.georeminder.data.FeatureSettings
import cz.jenda.georeminder.data.LanguageController
import cz.jenda.georeminder.data.localizedSubtitle
import cz.jenda.georeminder.model.Reminder
import java.util.Locale

/** Hlasité čtení připomínek nahlas v češtině. */
object TtsSpeaker {
    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    private val pendingQueue = mutableListOf<String>()

    @Synchronized
    fun init(context: Context) {
        if (tts != null) return
        val appContext = context.applicationContext
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = LanguageController.getLocale(FeatureSettings.appLanguage.value)
                val res = tts?.setLanguage(locale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                isInitialized = true
                synchronized(pendingQueue) {
                    for (text in pendingQueue) {
                        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "queued_${System.currentTimeMillis()}")
                    }
                    pendingQueue.clear()
                }
            }
        }
    }

    fun speakIfEnabled(context: Context, reminder: Reminder) {
        if (!FeatureSettings.ttsEnabled.value) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        init(context)

        val textToSpeak = if (FeatureSettings.ttsReadFullText.value) {
            "${reminder.title}. ${reminder.localizedSubtitle(context)}"
        } else {
            reminder.title
        }

        synchronized(pendingQueue) {
            if (isInitialized) {
                tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "reminder_${reminder.id}")
            } else {
                pendingQueue.add(textToSpeak)
            }
        }
    }

    fun speakText(context: Context, text: String) {
        init(context)
        val textToSpeak = text.ifEmpty { "GeoReminder pripomínka" }
        synchronized(pendingQueue) {
            if (isInitialized) {
                tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "sample_tts")
            } else {
                pendingQueue.add(textToSpeak)
            }
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        synchronized(pendingQueue) {
            pendingQueue.clear()
        }
    }
}
