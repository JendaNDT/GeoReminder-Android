package cz.jenda.georeminder.data

import android.content.Context
import cz.jenda.georeminder.model.FavoritePlace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/** Úložiště oblíbených míst – JSON soubor, stejný princip jako ReminderStore. */
class FavoritesStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val ioScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO.limitedParallelism(1)
    )

    private val _favorites = MutableStateFlow<List<FavoritePlace>>(emptyList())
    val favorites: StateFlow<List<FavoritePlace>> = _favorites

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

    @Synchronized
    fun reload() {
        val text = SharedStorage.readText(appContext, FILE) ?: return
        val decoded = try {
            SharedStorage.json.decodeFromString(
                ListSerializer(FavoritePlace.serializer()), text
            )
        } catch (_: Exception) {
            return
        }
        _favorites.value = decoded
    }

    @Synchronized
    fun add(place: FavoritePlace) {
        _favorites.value = _favorites.value + place
        persist()
    }

    @Synchronized
    fun update(place: FavoritePlace) {
        val list = _favorites.value.toMutableList()
        val index = list.indexOfFirst { it.id == place.id }
        if (index < 0) return
        list[index] = place
        _favorites.value = list
        persist()
    }

    @Synchronized
    fun delete(place: FavoritePlace) {
        _favorites.value = _favorites.value.filterNot { it.id == place.id }
        persist()
    }

    private fun persist() {
        val snapshot = _favorites.value
        ioScope.launch {
            try {
                val text = SharedStorage.json.encodeToString(
                    ListSerializer(FavoritePlace.serializer()), snapshot
                )
                SharedStorage.writeText(appContext, FILE, text)
            } catch (e: Exception) {
                android.util.Log.w("FavoritesStore", "Chyba při zápisu oblíbených", e)
            }
        }
    }
}
