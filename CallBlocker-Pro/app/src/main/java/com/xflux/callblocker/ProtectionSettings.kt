package com.xflux.callblocker

import android.content.Context

/**
 * Estado local da proteção. O padrão é true para preservar o comportamento
 * das instalações existentes que já utilizam o app como filtro de chamadas.
 */
object ProtectionSettings {
    private const val PREFS = "protection_settings"
    private const val KEY_ENABLED = "protection_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
