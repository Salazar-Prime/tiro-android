package com.salazarprime.tiro.accessibility

import android.content.Context

internal object OverlayConsent {
    private const val PREFERENCES = "tiro_accessibility_consent"
    private const val CONSENT_KEY = "focused_field_access_consent_v1"

    fun isGranted(context: Context): Boolean = context
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(CONSENT_KEY, false)

    fun grant(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CONSENT_KEY, true)
            .apply()
    }

    fun revoke(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(CONSENT_KEY)
            .apply()
    }
}
