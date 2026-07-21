package cz.jenda.georeminder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.ui.RootScreen
import cz.jenda.georeminder.ui.theme.GeoReminderTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    companion object {
        /** Požadavek ze zástupce na ploše: "time" / "location" → otevřít formulář. */
        val shortcutRequest = MutableStateFlow<String?>(null)

        /** Sdílený text s místem (z Map Google apod.) → předvyplnit připomínku. */
        val sharedPlaceText = MutableStateFlow<String?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderStore.get(this) // zahřátí úložiště
        handleIntent(intent)
        setContent {
            GeoReminderTheme {
                RootScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        intent.getStringExtra("shortcut_kind")?.let {
            shortcutRequest.value = it
            return
        }

        when (intent.action) {
            // „Sdílet" z Map Google (text s odkazem na místo)
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") == true) {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                        sharedPlaceText.value = it
                    }
                }
            }
            // geo: souřadnice z jiných aplikací
            Intent.ACTION_VIEW -> {
                intent.dataString?.takeIf { it.startsWith("geo:") }?.let {
                    sharedPlaceText.value = it
                }
            }
        }
    }
}
