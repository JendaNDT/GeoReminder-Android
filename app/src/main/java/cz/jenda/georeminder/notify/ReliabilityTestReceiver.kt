package cz.jenda.georeminder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cz.jenda.georeminder.data.ReliabilityEventType
import cz.jenda.georeminder.data.ReliabilityHistory

class ReliabilityTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        ReliabilityHistory.record(
            context,
            ReliabilityEventType.TEST_TRIGGERED,
        )
        NotificationHelper.showReliabilityTest(context)
    }

    companion object {
        const val ACTION_FIRE = "cz.jenda.georeminder.RELIABILITY_TEST_FIRE"
    }
}
