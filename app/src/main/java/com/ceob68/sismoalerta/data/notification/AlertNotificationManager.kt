package com.ceob68.sismoalerta.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ceob68.sismoalerta.R
import com.ceob68.sismoalerta.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class AlertNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val criticalChannel = NotificationChannel(
                CHANNEL_ID_CRITICAL,
                context.getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Alertas sísmicas críticas con sonido de alarma"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setShowBadge(true)
            }
            
            val normalChannel = NotificationChannel(
                CHANNEL_ID_NORMAL,
                "Alertas Sísmicas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de sismos detectados"
                enableLights(true)
                lightColor = android.graphics.Color.YELLOW
                enableVibration(true)
                sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setShowBadge(true)
            }
            
            val infoChannel = NotificationChannel(
                CHANNEL_ID_INFO,
                "Información Sísmica",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Información general sobre sismos"
                enableVibration(false)
            }
            
            notificationManager.createNotificationChannels(
                listOf(criticalChannel, normalChannel, infoChannel)
            )
            
            Timber.d("Canales de notificación creados")
        }
    }
    
    fun showCriticalSeismicAlert(
        magnitude: Double,
        place: String = "Detectado localmente",
        detectionType: String = "USGS"
    ) {
        val notificationId = Random.nextInt(10000, 99999)
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("sismo_magnitude", magnitude)
            putExtra("sismo_place", place)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CRITICAL)
            .setContentTitle(context.getString(R.string.alert_title))
            .setContentText(
                "${context.getString(R.string.alert_message)} $magnitude - $place"
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(
                android.graphics.BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.ic_launcher_foreground
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setLights(android.graphics.Color.RED, 500, 500)
            .addAction(
                0,
                "Ver detalles",
                pendingIntent
            )
            .build()
        
        notificationManager.notify(notificationId, notification)
        Timber.w("Notificación de alerta crítica enviada: Mag=$magnitude")
    }
    
    fun showNormalSeismicAlert(
        magnitude: Double,
        place: String,
        timeMillis: Long
    ) {
        val notificationId = Random.nextInt(10000, 99999)
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("sismo_magnitude", magnitude)
            putExtra("sismo_place", place)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_NORMAL)
            .setContentTitle("Sismo detectado")
            .setContentText("Magnitud $magnitude en $place")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
    
    fun showInfoNotification(
        magnitude: Double,
        place: String
    ) {
        val notificationId = Random.nextInt(10000, 99999)
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_INFO)
            .setContentTitle("Registro sísmico")
            .setContentText("Magnitud $magnitude registrada en $place")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        notificationManager.notify(notificationId, notification)
    }
    
    fun showSeismicAlert(
        magnitude: Double,
        place: String,
        timeMillis: Long = System.currentTimeMillis()
    ) {
        when {
            magnitude >= 4.5 -> showCriticalSeismicAlert(magnitude, place)
            magnitude >= 3.0 -> showNormalSeismicAlert(magnitude, place, timeMillis)
            else -> showInfoNotification(magnitude, place)
        }
    }
    
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
    
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
    
    companion object {
        private const val CHANNEL_ID_CRITICAL = "sismo_alert_critical"
        private const val CHANNEL_ID_NORMAL = "sismo_alert_normal"
        private const val CHANNEL_ID_INFO = "sismo_alert_info"
    }
}