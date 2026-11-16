package io.github.owbua.otai.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    /** „Überrasch mich“: so viele nötige Zutaten dürfen fehlen (0 = alles muss da sein). */
    val surpriseMaxMissing: Int = DEFAULT_MAX_MISSING,
    /** „Überrasch mich“: so viele Rezepte mindestens anstreben. */
    val surpriseMinResults: Int = DEFAULT_MIN_RESULTS,
    /** „Überrasch mich“: true = aus dem Vorrat, false = beliebige Rezepte. */
    val surpriseUsePantry: Boolean = true,
    /** Filter „Aus Vorrat“ in der Übersicht: so viele nötige Zutaten dürfen fehlen. */
    val pantryFilterMaxMissing: Int = 0,
) {
    companion object {
        const val DEFAULT_MAX_MISSING = 1
        const val MAX_MISSING_LIMIT = 3
        const val DEFAULT_MIN_RESULTS = 5
        val MIN_RESULTS_OPTIONS = listOf(3, 5, 8, 10)
    }
}

/** App-Einstellungen (SharedPreferences) – klein, synchron gelesen, als Flow beobachtbar. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    private fun read() = Settings(
        surpriseMaxMissing = prefs.getInt(KEY_MAX_MISSING, Settings.DEFAULT_MAX_MISSING).coerceIn(0, Settings.MAX_MISSING_LIMIT),
        surpriseMinResults = prefs.getInt(KEY_MIN_RESULTS, Settings.DEFAULT_MIN_RESULTS).coerceIn(1, 10),
        surpriseUsePantry = prefs.getBoolean(KEY_USE_PANTRY, true),
        pantryFilterMaxMissing = prefs.getInt(KEY_FILTER_MAX_MISSING, 0).coerceIn(0, Settings.MAX_MISSING_LIMIT),
    )

    fun setPantryFilterMaxMissing(n: Int) {
        val v = n.coerceIn(0, Settings.MAX_MISSING_LIMIT)
        prefs.edit().putInt(KEY_FILTER_MAX_MISSING, v).apply()
        _state.value = _state.value.copy(pantryFilterMaxMissing = v)
    }

    fun setSurpriseMinResults(n: Int) {
        val v = n.coerceIn(1, 10)
        prefs.edit().putInt(KEY_MIN_RESULTS, v).apply()
        _state.value = _state.value.copy(surpriseMinResults = v)
    }

    fun setSurpriseUsePantry(on: Boolean) {
        prefs.edit().putBoolean(KEY_USE_PANTRY, on).apply()
        _state.value = _state.value.copy(surpriseUsePantry = on)
    }

    fun setSurpriseMaxMissing(n: Int) {
        val v = n.coerceIn(0, Settings.MAX_MISSING_LIMIT)
        prefs.edit().putInt(KEY_MAX_MISSING, v).apply()
        _state.value = _state.value.copy(surpriseMaxMissing = v)
    }

    private companion object {
        const val KEY_MAX_MISSING = "ueberrasch_max_fehlend"
        const val KEY_MIN_RESULTS = "ueberrasch_min_treffer"
        const val KEY_USE_PANTRY = "ueberrasch_vorrat"
        const val KEY_FILTER_MAX_MISSING = "filter_vorrat_max_fehlend"
    }
}
