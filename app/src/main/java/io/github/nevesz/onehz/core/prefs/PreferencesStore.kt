package io.github.nevesz.onehz.core.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * DataStore-backed replacement for the legacy SharedPreferences file.
 *
 * The engine needs synchronous reads, so this class keeps an in-memory snapshot and writes
 * to DataStore asynchronously. The snapshot is loaded once in [initialize] during application
 * startup, before any preference is read; that one-time blocking read also performs the
 * migration from the old SharedPreferences XML.
 */
object PreferencesStore {
    private const val FILE_NAME = "onehz_prefs"
    private const val MIGRATION_KEY = "__migrated_from_shared_prefs"

    private val migratedKey = booleanPreferencesKey(MIGRATION_KEY)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val cache = HashMap<String, Any?>()
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()
    @Volatile
    private var delegate: SharedPreferences? = null

    fun initialize(context: Context) {
        if (delegate != null) return
        synchronized(this) {
            if (delegate != null) return
            val app = context.applicationContext
            val old = app.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            val loaded = runBlocking {
                val current = runCatching { app.oneHzDataStore.data.first() }
                    .getOrElse { emptyPreferences() }
                if (current[migratedKey] == true) {
                    current
                } else {
                    val oldValues = old.all
                    app.oneHzDataStore.edit { mutable ->
                        if (oldValues.isNotEmpty()) {
                            oldValues.forEach { (key, value) -> mutable.putValue(key, value) }
                            old.edit().clear().apply()
                        }
                        mutable[migratedKey] = true
                    }
                }
            }
            loaded.asMap().forEach { (key, value) -> cache[key.name] = value }
            delegate = DataStoreSharedPreferences(app, cache, scope, mutex, listeners)
        }
    }

    fun get(context: Context): SharedPreferences {
        delegate?.let { return it }
        initialize(context)
        return delegate ?: error("PreferencesStore is not initialized")
    }
}

/** Minimal SharedPreferences facade over the DataStore cache. */
private class DataStoreSharedPreferences(
    private val context: Context,
    private val cache: HashMap<String, Any?>,
    private val scope: CoroutineScope,
    private val mutex: Mutex,
    private val listeners: CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>,
) : SharedPreferences {

    override fun getAll(): MutableMap<String, *> = synchronized(cache) { HashMap(cache) }

    override fun getString(key: String?, defValue: String?): String? =
        synchronized(cache) { cache[key] as? String } ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        synchronized(cache) { (cache[key] as? Set<String>)?.toMutableSet() } ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        synchronized(cache) { cache[key] as? Int } ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        synchronized(cache) { cache[key] as? Long } ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        synchronized(cache) { cache[key] as? Float } ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        synchronized(cache) { cache[key] as? Boolean } ?: defValue

    override fun contains(key: String?): Boolean = synchronized(cache) { cache.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        if (listener != null) listeners.addIfAbsent(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        if (listener != null) listeners.remove(listener)
    }

    private inner class Editor : SharedPreferences.Editor {
        private val puts = LinkedHashMap<String, Any?>()
        private val removals = LinkedHashSet<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = also { puts[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = also {
            puts[key] = values?.toSet()
        }
        override fun putInt(key: String, value: Int) = also { puts[key] = value }
        override fun putLong(key: String, value: Long) = also { puts[key] = value }
        override fun putFloat(key: String, value: Float) = also { puts[key] = value }
        override fun putBoolean(key: String, value: Boolean) = also { puts[key] = value }
        override fun remove(key: String) = also { removals.add(key) }
        override fun clear() = also { clear = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            val changed = LinkedHashSet<String>()
            synchronized(cache) {
                if (clear) {
                    changed.addAll(cache.keys)
                    cache.clear()
                }
                removals.forEach {
                    if (cache.remove(it) != null) changed.add(it)
                }
                puts.forEach { (key, value) ->
                    cache[key] = value
                    changed.add(key)
                }
            }
            changed.forEach { key ->
                listeners.forEach { it.onSharedPreferenceChanged(this@DataStoreSharedPreferences, key) }
            }
            val snapshotPuts = LinkedHashMap(puts)
            val snapshotRemovals = LinkedHashSet(removals)
            val snapshotClear = clear
            scope.launch {
                mutex.withLock {
                    context.oneHzDataStore.edit { mutable ->
                        if (snapshotClear) mutable.clear()
                        snapshotRemovals.forEach { key -> mutable.removeKey(key) }
                        snapshotPuts.forEach { (key, value) -> mutable.putValue(key, value) }
                    }
                }
            }
        }
    }
}

private fun MutablePreferences.removeKey(key: String) {
    listOf(
        stringPreferencesKey(key),
        intPreferencesKey(key),
        longPreferencesKey(key),
        floatPreferencesKey(key),
        booleanPreferencesKey(key),
        stringSetPreferencesKey(key),
    ).forEach { remove(it) }
}

private fun MutablePreferences.putValue(key: String, value: Any?) {
    when (value) {
        is Boolean -> this[booleanPreferencesKey(key)] = value
        is Int -> this[intPreferencesKey(key)] = value
        is Long -> this[longPreferencesKey(key)] = value
        is Float -> this[floatPreferencesKey(key)] = value
        is String -> this[stringPreferencesKey(key)] = value
        is Set<*> -> {
            @Suppress("UNCHECKED_CAST")
            this[stringSetPreferencesKey(key)] = value.filterIsInstance<String>().toSet()
        }
    }
}

private val Context.oneHzDataStore by preferencesDataStore(
    name = "onehz_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
