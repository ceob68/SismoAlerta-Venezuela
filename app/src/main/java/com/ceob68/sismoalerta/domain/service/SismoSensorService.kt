package com.ceob68.sismoalerta.domain.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ceob68.sismoalerta.R
import com.ceob68.sismoalerta.data.notification.AlertNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Foreground Service que monitorea el acelerómetro del dispositivo
 * para detectar vibraciones sísmicas en tiempo real.
 */
@AndroidEntryPoint
class SismoSensorService : Service(), SensorEventListener {
    
    @Inject
    lateinit var notificationManager: AlertNotificationManager
    
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    private val accelerationThreshold = 5.0 // m/s² (aproximadamente 0.5g)
    private val samplingWindow = 100 // ms
    private var lastAlertTime = 0L
    private val alertCooldown = 5000L // 5 segundos entre alertas
    
    private val accelerationBuffer = ArrayDeque<Double>(maxSize = 50)
    private var lastTimestamp = 0L
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("SismoSensorService creado")
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        if (accelerometer == null) {
            Timber.e("Acelerómetro no disponible en este dispositivo")
            stopSelf()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("SismoSensorService iniciado")
        
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID_SERVICE, notification)
        
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_FASTEST
            )
            Timber.d("Acelerómetro registrado: ${it.name}")
        }
        
        return START_STICKY
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        
        try {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            val totalAcceleration = sqrt(x * x + y * y + z * z)
            val adjustedAcceleration = totalAcceleration - SensorManager.STANDARD_GRAVITY
            
            accelerationBuffer.addLast(kotlin.math.abs(adjustedAcceleration))
            
            val currentTime = event.timestamp / 1_000_000L
            
            if (lastTimestamp > 0) {
                val timeDelta = currentTime - lastTimestamp
                
                if (timeDelta >= samplingWindow) {
                    val detectedAcceleration = calculateHighPassFilter()
                    
                    if (detectedAcceleration > accelerationThreshold) {
                        triggerSeismicAlert(detectedAcceleration)
                    }
                    
                    lastTimestamp = currentTime
                    accelerationBuffer.clear()
                }
            } else {
                lastTimestamp = currentTime
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error procesando evento del sensor")
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No se requiere acción
    }
    
    private fun calculateHighPassFilter(): Double {
        if (accelerationBuffer.size < 2) return 0.0
        
        val values = accelerationBuffer.toList()
        val mean = values.average()
        
        val variance = values.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        
        val peaks = values.count { kotlin.math.abs(it - mean) > 2 * stdDev }
        
        return if (peaks > values.size / 3) stdDev else 0.0
    }
    
    private fun triggerSeismicAlert(detectedAcceleration: Double) {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastAlertTime < alertCooldown) {
            return
        }
        
        lastAlertTime = currentTime
        
        Timber.w("🔴 ¡ALERTA SÍSMICA DETECTADA! Aceleración: $detectedAcceleration m/s²")
        
        notificationManager.showCriticalSeismicAlert(
            magnitude = estimateMagnitudeFromAcceleration(detectedAcceleration),
            detectionType = "Local (Acelerómetro)"
        )
        
        sendBroadcast(
            Intent("com.ceob68.sismoalerta.SISMO_ALERT").apply {
                putExtra("acceleration", detectedAcceleration)
                putExtra("timestamp", currentTime)
            }
        )
    }
    
    private fun estimateMagnitudeFromAcceleration(acceleration: Double): Double {
        val magnitude = kotlin.math.log10(kotlin.math.max(acceleration, 0.1)) + 1.5
        return kotlin.math.max(2.0, kotlin.math.min(magnitude, 9.0))
    }
    
    private fun buildForegroundNotification(): NotificationCompat.Notification {
        val channelId = "sismo_monitoring_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                getString(R.string.notification_channel_service),
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoreo continuo de sismos"
            }
            
            val notificationMgr = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationMgr.createNotificationChannel(channel)
        }
        
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.sensor_service_title))
            .setContentText(getString(R.string.sensor_service_message))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("SismoSensorService destruido")
        
        sensorManager.unregisterListener(this)
        accelerationBuffer.clear()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    companion object {
        private const val NOTIFICATION_ID_SERVICE = 1001
        
        fun startService(context: Context) {
            val intent = Intent(context, SismoSensorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            context.stopService(Intent(context, SismoSensorService::class.java))
        }
    }
}