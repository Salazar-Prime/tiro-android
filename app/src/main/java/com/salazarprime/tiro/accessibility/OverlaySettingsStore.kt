package com.salazarprime.tiro.accessibility

import android.content.Context
import android.content.SharedPreferences

internal class OverlaySettingsStore(context: Context) {
    data class Settings(
        val sizeDp: Int,
        val opacityPercent: Int,
        val releaseDelayMillis: Int,
        val xFraction: Float,
        val yFraction: Float,
    )

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        sizeDp = preferences.getInt(SIZE_DP, DEFAULT_SIZE_DP)
            .coerceIn(MIN_SIZE_DP, MAX_SIZE_DP),
        opacityPercent = preferences.getInt(OPACITY_PERCENT, DEFAULT_OPACITY_PERCENT)
            .coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT),
        releaseDelayMillis = preferences.getInt(
            RELEASE_DELAY_MILLIS,
            DEFAULT_RELEASE_DELAY_MILLIS,
        ).coerceIn(MIN_RELEASE_DELAY_MILLIS, MAX_RELEASE_DELAY_MILLIS),
        xFraction = preferences.getFloat(X_FRACTION, DEFAULT_X_FRACTION)
            .coerceIn(0f, 1f),
        yFraction = preferences.getFloat(Y_FRACTION, DEFAULT_Y_FRACTION)
            .coerceIn(0f, 1f),
    )

    fun setSizeDp(value: Int) {
        preferences.edit().putInt(SIZE_DP, value.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)).apply()
    }

    fun setOpacityPercent(value: Int) {
        preferences.edit()
            .putInt(
                OPACITY_PERCENT,
                value.coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT),
            )
            .apply()
    }

    fun setReleaseDelayMillis(value: Int) {
        preferences.edit()
            .putInt(
                RELEASE_DELAY_MILLIS,
                value.coerceIn(MIN_RELEASE_DELAY_MILLIS, MAX_RELEASE_DELAY_MILLIS),
            )
            .apply()
    }

    fun setPosition(xFraction: Float, yFraction: Float) {
        preferences.edit()
            .putFloat(X_FRACTION, xFraction.coerceIn(0f, 1f))
            .putFloat(Y_FRACTION, yFraction.coerceIn(0f, 1f))
            .apply()
    }

    fun resetPosition() {
        preferences.edit().remove(X_FRACTION).remove(Y_FRACTION).apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val MIN_SIZE_DP = 48
        const val MAX_SIZE_DP = 96
        const val DEFAULT_SIZE_DP = 72
        const val MIN_OPACITY_PERCENT = 35
        const val MAX_OPACITY_PERCENT = 100
        const val DEFAULT_OPACITY_PERCENT = 100
        const val MIN_RELEASE_DELAY_MILLIS = 0
        const val MAX_RELEASE_DELAY_MILLIS = 2_000
        const val DEFAULT_RELEASE_DELAY_MILLIS = 450

        private const val PREFERENCES = "tiro_overlay_settings"
        private const val SIZE_DP = "size_dp_v1"
        private const val OPACITY_PERCENT = "opacity_percent_v1"
        private const val RELEASE_DELAY_MILLIS = "release_delay_millis_v1"
        private const val X_FRACTION = "x_fraction_v1"
        private const val Y_FRACTION = "y_fraction_v1"
        private const val DEFAULT_X_FRACTION = 1f
        private const val DEFAULT_Y_FRACTION = 0.42f
    }
}
