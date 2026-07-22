package cz.jenda.georeminder.notify

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import cz.jenda.georeminder.data.FeatureSettings
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
                val csLocale = Locale("cs", "CZ")
                val res = tts?.setLanguage(csLocale)
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
        init(context)
        if (!FeatureSettings.ttsEnabled.value) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        val textToSpeak = if (FeatureSettings.ttsReadFullText.value) {
            "${reminder.title}. ${reminder.subtitle}"
        } else {
            reminder.title
        }

        if (isInitialized) {
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "reminder_${reminder.id}")
        } else {
            synchronized(pendingQueue) {
                pendingQueue.add(textToSpeak)
            }
        }
    }

    fun speakText(context: Context, text: String) {
        init(context)
        val textToSpeak = text.ifEmpty { "GeoReminder pripomínka" }
        if (isInitialized) {
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "sample_tts")
        } else {
            synchronized(pendingQueue) {
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
