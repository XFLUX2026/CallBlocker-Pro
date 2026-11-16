package com.xflux.callblocker

import android.telecom.Call
import android.telecom.CallScreeningService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallBlockerService : CallScreeningService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val raw = details.handle?.schemeSpecificPart.orEmpty()
        val allowed = AllowList.isAllowed(this, raw)
        val response = CallResponse.Builder()
            .setDisallowCall(!allowed).setRejectCall(!allowed).setSkipNotification(!allowed).build()
        respondToCall(details, response)
        if (!allowed) {
            val number = raw.ifBlank { "Número oculto" }
            val reason = if (raw.isBlank()) "Número oculto" else "Número não autorizado"
            serviceScope.launch { HistoryStore.add(applicationContext, number, reason) }
        }
    }
}
