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
import androidx.core.app.ServiceCompat
import com.ceob68.sismoalerta.R
import com.ceob68.sismoalerta.data.notification.AlertNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Foreground Service que monitorea el acelerómetro del dispositivo
 * para detectar vibraciones sísmicas en tiempo real.
 * 
 * Umbral de detección: aceleración > 0.5g (aproximadamente 5 m/s²)
 * Filtro: Detecta cambios de alta frecuencia (> 2Hz)
 * 
 * Android 14 (API 34): Requiere especificar explícitamente el tipo de servicio
 */
@AndroidEntryPoint
class SismoSensorService : Service(), SensorEventListener {
    
    @Inject
    lateinit var notificationManager: AlertNotificationManager
    
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    // Parámetros de detección
    private val accelerationThreshold = 5.0 // m/s² (aproximadamente 0.5g)
    private val samplingWindow = 100 // ms
    private var lastAlertTime = 0L
    private val alertCooldown = 5000L // 5 segundos entre alertas
    
    // Buffer para calcular media móvil (filtro paso alto)
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
        
        // Android 14 (API 34) requiere especificar el tipo de servicio explícitamente
        // Lanza excepción en tiempo de ejecución si no se especifica
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID_SERVICE,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
                Timber.d("Foreground service iniciado correctamente con tipo LOCATION (Android 14+)")
            } catch (e: Exception) {
                Timber.e(e, "Error al iniciar foreground service en Android 14+, usando fallback")
                // Fallback para versiones anteriores o si falla
                startForeground(NOTIFICATION_ID_SERVICE, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID_SERVICE, notification)
        }
        
        // Registrar listener del sensor con máxima precisión
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
            // Obtener aceleración en los 3 ejes (sin incluir gravedad)
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            // Calcular magnitud de aceleración (sin considerar gravedad ~9.8 m/s²)
            val totalAcceleration = sqrt(x * x + y * y + z * z)
            
            // Ajustar por gravedad (filtro simple)
            val adjustedAcceleration = totalAcceleration - SensorManager.STANDARD_GRAVITY
            
            // Añadir al buffer
            accelerationBuffer.addLast(kotlin.math.abs(adjustedAcceleration))
            
            // Detectar cambios de alta frecuencia usando derivada
            val currentTime = event.timestamp / 1_000_000L // Convertir a ms
            
            if (lastTimestamp > 0) {
                val timeDelta = currentTime - lastTimestamp
                
                // Procesar cada ~100ms para evitar ruido
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
    
    /**
     * Aplica filtro paso alto para detectar cambios rápidos
     * (componentes de alta frecuencia asociadas con sismos)
     */
    private fun calculateHighPassFilter(): Double {
        if (accelerationBuffer.size < 2) return 0.0
        
        val values = accelerationBuffer.toList()
        val mean = values.average()
        
        // Calcular desviación estándar
        val variance = values.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        
        // Detectar picos (desviaciones > 2 desv. estándar)
        val peaks = values.count { kotlin.math.abs(it - mean) > 2 * stdDev }
        
        // Si hay múltiples picos, es probable un sismo
        return if (peaks > values.size / 3) stdDev else 0.0
    }
    
    /**
     * Dispara una alerta sísmica con cooldown para evitar spam
     */
    private fun triggerSeismicAlert(detectedAcceleration: Double) {
        val currentTime = System.currentTimeMillis()
        
        // Aplicar cooldown para evitar alertas repetidas
        if (currentTime - lastAlertTime < alertCooldown) {
            return
        }
        
        lastAlertTime = currentTime
        
        Timber.w("🔴 ¡ALERTA SÍSMICA DETECTADA! Aceleración: $detectedAcceleration m/s²")
        
        // Mostrar notificación de alerta
        notificationManager.showCriticalSeismicAlert(
            magnitude = estimateMagnitudeFromAcceleration(detectedAcceleration),
            detectionType = "Local (Acelerómetro)"
        )
        
        // Enviar broadcast para que la app lo procese si está abierta
        sendBroadcast(
            Intent("com.ceob68.sismoalerta.SISMO_ALERT").apply {
                putExtra("acceleration", detectedAcceleration)
                putExtra("timestamp", currentTime)
            }
        )
    }
    
    /**
     * Estima la magnitud basada en la aceleración detectada
     * Usando relación empírica: Mag ≈ log10(Aceleración) + 1.5
     */
    private fun estimateMagnitudeFromAcceleration(acceleration: Double): Double {
        val magnitude = kotlin.math.log10(kotlin.math.max(acceleration, 0.1)) + 1.5
        return kotlin.math.max(2.0, kotlin.math.min(magnitude, 9.0))
    }
    
    /**
     * Construye la notificación del Foreground Service
     */
    private fun buildForegroundNotification(): NotificationCompat.Notification {
        val channelId = "sismo_monitoring_channel"
        
        // Crear canal de notificación para Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                getString(R.string.notification_channel_service),
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoreo continuo de sismos en tiempo real"
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
        
        // Desregistrar el listener del sensor
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