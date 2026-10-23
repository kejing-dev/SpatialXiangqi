package com.kejing.spatialxiangqi.data.repository

import android.content.Context
import com.kejing.spatialxiangqi.domain.model.AppSettings
import com.kejing.spatialxiangqi.domain.model.BoardMode
import com.kejing.spatialxiangqi.domain.model.Language
import com.kejing.spatialxiangqi.domain.model.PieceFace
import com.kejing.spatialxiangqi.domain.model.Skin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Persistent app settings (placement & display, preferences). */
interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    fun update(transform: (AppSettings) -> AppSettings)
}

/** In-memory implementation for tests and previews. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state
    override fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

/** SharedPreferences-backed implementation used by the app. */
class PrefsSettingsRepository(context: Context) : SettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences("xiangqi_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    override val settings: StateFlow<AppSettings> = state

    override fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        state.value = next
        prefs.edit()
            .putString("language", next.language.name)
            .putString("mode", next.mode.name)
            .putString("skin", next.skin.name)
            .putString("pieceFace", next.pieceFace.name)
            .putInt("notation", next.notation)
            .putBoolean("sounds", next.sounds)
            .putBoolean("haptics", next.haptics)
            .putInt("boardScalePercent", next.boardScalePercent)
            .putInt("orientationDeg", next.orientationDeg)
            .putInt("heightTrimCm", next.heightTrimCm)
            .putInt("tiltDeg", next.tiltDeg)
            .putBoolean("showCoordinates", next.showCoordinates)
            .putBoolean("reduceMotion", next.reduceMotion)
            .putBoolean("firstRunDone", next.firstRunDone)
            .putBoolean("engineOn", next.engineOn)
            .putInt("level", next.level)
            .apply()
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        fun <T : Enum<T>> enumOf(key: String, default: T, values: Array<T>): T =
            prefs.getString(key, null)?.let { name -> values.firstOrNull { it.name == name } } ?: default
        return AppSettings(
            language = enumOf("language", d.language, Language.values()),
            mode = enumOf("mode", d.mode, BoardMode.values()),
            skin = enumOf("skin", d.skin, Skin.values()),
            pieceFace = enumOf("pieceFace", d.pieceFace, PieceFace.values()),
            notation = prefs.getInt("notation", d.notation),
            sounds = prefs.getBoolean("sounds", d.sounds),
            haptics = prefs.getBoolean("haptics", d.haptics),
            boardScalePercent = prefs.getInt("boardScalePercent", d.boardScalePercent),
            orientationDeg = prefs.getInt("orientationDeg", d.orientationDeg),
            heightTrimCm = prefs.getInt("heightTrimCm", d.heightTrimCm),
            tiltDeg = prefs.getInt("tiltDeg", d.tiltDeg),
            showCoordinates = prefs.getBoolean("showCoordinates", d.showCoordinates),
            reduceMotion = prefs.getBoolean("reduceMotion", d.reduceMotion),
            firstRunDone = prefs.getBoolean("firstRunDone", d.firstRunDone),
            engineOn = prefs.getBoolean("engineOn", d.engineOn),
            level = prefs.getInt("level", d.level),
        )
    }
}
