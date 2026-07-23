package cz.jenda.georeminder.data

import android.content.Context
import android.util.Log
import cz.jenda.georeminder.model.FavoritePlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/** Úložiště oblíbených míst – JSON soubor, stejný princip jako ReminderStore. */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val ioScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    @Volatile
    private var loadFailed = false

    private val _favorites = MutableStateFlow<List<FavoritePlace>>(emptyList())
    val favorites: StateFlow<List<FavoritePlace>> = _favorites

    private val _storageError = MutableStateFlow(false)
    val storageError: StateFlow<Boolean> = _storageError

    init {
        reload()
    }

    companion object {
        private const val FILE = "favorites.json"

        @Volatile
        private var instance: FavoritesStore? = null

        fun get(context: Context): FavoritesStore =
            instance ?: synchronized(this) {
                instance ?: FavoritesStore(context).also { instance = it }
            }
    }

    fun reload() {
        ioScope.launch { reloadFromDisk() }
    }

    suspend fun reloadAndGet(): List<FavoritePlace> = withContext(ioDispatcher) {
        reloadFromDisk()
    }

    fun add(place: FavoritePlace) {
        ioScope.launch { addOnIo(place) }
    }

    suspend fun addDurably(place: FavoritePlace): Boolean = withContext(ioDispatcher) {
        addOnIo(place)
    }

    fun update(place: FavoritePlace) {
        ioScope.launch { updateOnIo(place) }
    }

    suspend fun updateDurably(place: FavoritePlace): Boolean = withContext(ioDispatcher) {
        updateOnIo(place)
    }

    fun delete(place: FavoritePlace) {
        ioScope.launch {
            if (!canWrite()) return@launch
            val next = _favorites.value.filterNot { it.id == place.id }
            if (persistSnapshot(next)) _favorites.value = next
        }
    }

    suspend fun upsertAllDurably(imported: List<FavoritePlace>): Boolean =
        withContext(ioDispatcher) {
            reloadFromDisk()
            if (!canWrite()) return@withContext false

            val merged = LinkedHashMap<String, FavoritePlace>()
            _favorites.value.forEach { merged[it.id] = it }
            imported.forEach { merged[it.id] = it }
            val next = merged.values.toList()

            if (persistSnapshot(next)) {
                _favorites.value = next
                true
            } else {
                false
            }
        }

    suspend fun replaceAllDurably(replacement: List<FavoritePlace>): Boolean =
        withContext(ioDispatcher) {
            if (!canWrite()) return@withContext false
            if (persistSnapshot(replacement)) {
                _favorites.value = replacement
                true
            } else {
                false
            }
        }

    private fun canWrite(): Boolean {
        if (loadFailed) {
            _storageError.value = true
            Log.w("FavoritesStore", "Uložení přeskočeno – poslední čtení oblíbených selhalo")
            return false
        }
        return true
    }

    private fun addOnIo(place: FavoritePlace): Boolean {
        if (!canWrite()) return false
        if (_favorites.value.any { it.id == place.id }) return false
        val next = _favorites.value + place
        if (!persistSnapshot(next)) return false
        _favorites.value = next
        return true
    }

    private fun updateOnIo(place: FavoritePlace): Boolean {
        if (!canWrite()) return false
        val list = _favorites.value.toMutableList()
        val index = list.indexOfFirst { it.id == place.id }
        if (index < 0) return false
        list[index] = place
        if (!persistSnapshot(list)) return false
        _favorites.value = list
        return true
    }

    private fun reloadFromDisk(): List<FavoritePlace> {
        when (val result = SharedStorage.read(appContext, FILE)) {
            is SharedStorage.ReadResult.Ok -> {
                when (val decoded = SharedStorage.decodeFavoritesResult(result.text)) {
                    is SharedStorage.DecodeResult.Ok -> {
                        _favorites.value = decoded.value
                        loadFailed = decoded.hadInvalidEntries
                        _storageError.value = decoded.hadInvalidEntries
                    }
                    SharedStorage.DecodeResult.Error -> {
                        loadFailed = true
                        _storageError.value = true
                        Log.w("FavoritesStore", "Dekódování oblíbených selhalo – soubor nebude přepsán")
                    }
                }
            }
            SharedStorage.ReadResult.Empty -> {
                _favorites.value = emptyList()
                loadFailed = false
                _storageError.value = false
            }
            SharedStorage.ReadResult.Error -> {
                loadFailed = true
                _storageError.value = true
                Log.w("FavoritesStore", "Čtení oblíbených selhalo – soubor nebude přepsán")
            }
        }
        return _favorites.value
    }

    private fun persistSnapshot(snapshot: List<FavoritePlace>): Boolean = try {
        val text = SharedStorage.json.encodeToString(
            ListSerializer(FavoritePlace.serializer()), snapshot
        )
        SharedStorage.writeText(appContext, FILE, text).also { success ->
            _storageError.value = !success
        }
    } catch (e: Exception) {
        _storageError.value = true
        Log.w("FavoritesStore", "Chyba při zápisu oblíbených", e)
        false
    }
}
