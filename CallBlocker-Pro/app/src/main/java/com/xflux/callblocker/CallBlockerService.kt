package com.xflux.callblocker

import android.telecom.Call
import android.telecom.CallScreeningService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class CallBlockerService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        // Com a proteção pausada, o serviço apenas libera a chamada sem aplicar filtros.
        if (!ProtectionSettings.isEnabled(this)) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        val raw = details.handle?.schemeSpecificPart.orEmpty()
        val manuallyBlocked = BlockList.contains(this, raw)
        val allowed = !manuallyBlocked && AllowList.isAllowed(this, raw)
        val response = CallResponse.Builder()
            .setDisallowCall(!allowed)
            .setRejectCall(!allowed)
            .setSkipNotification(!allowed)
            .build()

        // Responder primeiro é essencial: o Android exige resposta em até 5 segundos.
        respondToCall(details, response)

        if (!allowed) {
            val number = raw.ifBlank { "Número oculto" }
            val reason = when {
                manuallyBlocked -> "Bloqueado manualmente"
                raw.isBlank() -> "Número oculto"
                else -> "Número não autorizado"
            }
            // A gravação ocorre depois da resposta ao Android, sem perder a operação
            // ao encerrar uma coroutine pertencente ao serviço.
            runBlocking(Dispatchers.IO) {
                HistoryStore.add(applicationContext, number, reason)
            }
        }
    }
}