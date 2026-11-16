package com.xflux.callblocker

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.PhoneNumberUtils
import android.net.Uri
import android.provider.ContactsContract

object AllowList {
    private const val PREF = "allow_list"
    fun manual(context: Context): Set<String> = context.getSharedPreferences(PREF, 0)
        .getStringSet("numbers", emptySet())?.toSet() ?: emptySet()
    fun add(context: Context, number: String) {
        val clean = normalize(number)
        if (clean.isBlank()) return
        context.getSharedPreferences(PREF, 0).edit()
            .putStringSet("numbers", (manual(context) + clean).toMutableSet()).apply()
    }
    fun remove(context: Context, number: String) {
        context.getSharedPreferences(PREF, 0).edit()
            .putStringSet("numbers", (manual(context) - normalize(number)).toMutableSet()).apply()
    }
    fun isAllowed(context: Context, raw: String): Boolean {
        if (raw.isBlank()) return false
        if (manual(context).any { PhoneNumberUtils.compare(it, raw) }) return true
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(raw))
        return try {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        } catch (_: SecurityException) { false } catch (_: Exception) { false }
    }
    private fun normalize(value: String): String = value.filter { it.isDigit() }.let {
        if (it.startsWith("00")) it.drop(2) else it
    }
}
