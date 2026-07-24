package cz.jenda.georeminder

import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.data.SharedStorage
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderStoreTest {

    @Test
    fun reloadAndGetWaitsForColdDiskLoad() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val expected = Reminder(
            id = "COLD-START-REMINDER",
            title = "Studený start",
            kind = ReminderKind.TIME,
            dueDate = System.currentTimeMillis() + 60_000L,
            timeRepeat = TimeRepeat.NEVER,
        )
        val json = SharedStorage.json.encodeToString(
            ListSerializer(Reminder.serializer()),
            listOf(expected),
        )
        SharedStorage.writeText(context, "reminders.json", json)

        // get() zahájí asynchronní init reload. Regresní kontrakt je, že
        // reloadAndGet() nevrátí výchozí prázdný StateFlow, ale počká na disk.
        val loaded = ReminderStore.get(context).reloadAndGet()

        assertEquals(listOf(expected), loaded)
    }
}
