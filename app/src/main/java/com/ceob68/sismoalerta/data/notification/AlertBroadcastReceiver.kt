package com.ceob68.sismoalerta.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ceob68.sismoalerta.domain.service.SismoSensorService
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AlertBroadcastReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var notificationManager: AlertNotificationManager
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return
        
        try {
            when (intent.action) {
                "com.ceob68.sismoalerta.SISMO_ALERT" -> {
                    handleSeismicAlert(context, intent)
                }
                Intent.ACTION_BOOT_COMPLETED -> {
                    Timber.d("Dispositivo reiniciado, iniciando servicio de sensores")
                    SismoSensorService.startService(context)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error en AlertBroadcastReceiver")
        }
    }
    
    private fun handleSeismicAlert(context: Context, intent: Intent) {
        val acceleration = intent.getDoubleExtra("acceleration", 0.0)
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
        
        Timber.w("🔴 Broadcast de alerta sísmica recibido - Aceleración: $acceleration m/s²")
    }
}