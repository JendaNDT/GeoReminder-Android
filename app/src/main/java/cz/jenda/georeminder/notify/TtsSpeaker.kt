package cz.jenda.georeminder.notify

import android.content.Context
import android.content.res.Resources
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import cz.jenda.georeminder.R
import cz.jenda.georeminder.data.FeatureSettings
import cz.jenda.georeminder.data.LanguageController
import cz.jenda.georeminder.model.Reminder
import java.util.ArrayDeque
import java.util.Locale

/** Hlasité čtení připomínek s restartovatelným TTS enginem a omezenou frontou. */
object TtsSpeaker {
    private const val TAG = "TtsSpeaker"
    private const val MAX_PENDING_REQUESTS = 8

    private data class SpeechRequest(
        val text: String,
        val utteranceId: String,
    )

    private val lock = Any()
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var engineGeneration = 0L
    private val pendingQueue = ArrayDeque<SpeechRequest>()

    fun speakIfEnabled(context: Context, reminder: Reminder) {
        if (!FeatureSettings.ttsEnabled.value) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        val textToSpeak = if (FeatureSettings.ttsReadFullText.value) {
            "${reminder.title}. ${NotificationHelper.body(context, reminder)}"
        } else {
            reminder.title
        }

        enqueueOrSpeak(
            context = context,
            request = SpeechRequest(textToSpeak, "reminder_${reminder.id}"),
        )
    }

    /** Ukázka z Nastavení funguje i když je automatické TTS vypnuté. */
    fun speakText(context: Context, text: String) {
        val strings = LanguageController.localizedContext(context)
        val fallback = strings.getString(R.string.tts_test_message)
        enqueueOrSpeak(
            context = context,
            request = SpeechRequest(text.ifBlank { fallback }, "sample_tts"),
        )
    }

    private fun enqueueOrSpeak(context: Context, request: SpeechRequest) {
        val appContext = context.applicationContext
        var readyEngine: TextToSpeech? = null

        synchronized(lock) {
            if (isInitialized && tts != null) {
                readyEngine = tts
            } else {
                enqueueBoundedLocked(request)
                if (tts == null) {
                    startEngineLocked(appContext)
                }
            }
        }

        readyEngine?.let { speakNow(appContext, it, request, TextToSpeech.QUEUE_FLUSH) }
    }

    private fun enqueueBoundedLocked(request: SpeechRequest) {
        while (pendingQueue.size >= MAX_PENDING_REQUESTS) {
            pendingQueue.removeFirst()
        }
        pendingQueue.addLast(request)
    }

    /** Volat pouze uvnitř locku. */
    private fun startEngineLocked(context: Context) {
        val generation = ++engineGeneration
        tts = TextToSpeech(context) { status ->
            handleInitResult(context, generation, status)
        }
    }

    private fun handleInitResult(context: Context, generation: Long, status: Int) {
        var failedEngine: TextToSpeech? = null
        var readyEngine: TextToSpeech? = null
        var queued = emptyList<SpeechRequest>()

        synchronized(lock) {
            if (generation != engineGeneration) return
            val engine = tts ?: return

            if (status != TextToSpeech.SUCCESS) {
                failedEngine = engine
                tts = null
                isInitialized = false
                pendingQueue.clear()
            } else {
                isInitialized = true
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = Unit
                    override fun onError(utteranceId: String?) {
                        Log.w(TAG, "TTS syntéza selhala: $utteranceId")
                    }
                })
                readyEngine = engine
                queued = pendingQueue.toList()
                pendingQueue.clear()
            }
        }

        failedEngine?.let { engine ->
            runCatching { engine.shutdown() }
            Log.w(TAG, "Inicializace TTS enginu selhala; další požadavek může zkusit nový init")
            return
        }

        readyEngine?.let { engine ->
            configureLanguage(context, engine)
            queued.forEachIndexed { index, request ->
                speakNow(
                    context,
                    engine,
                    request,
                    if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                )
            }
        }
    }

    private fun speakNow(
        context: Context,
        engine: TextToSpeech,
        request: SpeechRequest,
        queueMode: Int,
    ) {
        runCatching {
            configureLanguage(context, engine)
            engine.speak(request.text, queueMode, null, request.utteranceId)
        }.onFailure {
            Log.w(TAG, "TTS speak selhal", it)
        }
    }

    private fun configureLanguage(context: Context, engine: TextToSpeech) {
        val preferred = resolvePreferredLocale(
            LanguageController.currentLanguageCode(),
            systemLocale(),
        )

        val result = engine.setLanguage(preferred)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = if (preferred.language == "en") {
                Locale.forLanguageTag("cs-CZ")
            } else {
                Locale.US
            }
            engine.setLanguage(fallback)
        }
    }

    private fun systemLocale(): Locale {
        val locales = Resources.getSystem().configuration.locales
        return if (!locales.isEmpty) locales[0] else Locale.US
    }

    /** SYSTEM používá angličtinu jen pro anglický systém, jinak český fallback. */
    internal fun resolvePreferredLocale(appLanguage: String, systemLocale: Locale): Locale =
        when (appLanguage) {
            LanguageController.LANG_CS -> Locale.forLanguageTag("cs-CZ")
            LanguageController.LANG_EN -> Locale.US
            else -> if (systemLocale.language.equals("en", ignoreCase = true)) {
                Locale.US
            } else {
                Locale.forLanguageTag("cs-CZ")
            }
        }

    fun shutdown() {
        val engine = synchronized(lock) {
            engineGeneration++
            val current = tts
            tts = null
            isInitialized = false
            pendingQueue.clear()
            current
        }
        if (engine != null) {
            runCatching { engine.stop() }
            runCatching { engine.shutdown() }
        }
    }
}
