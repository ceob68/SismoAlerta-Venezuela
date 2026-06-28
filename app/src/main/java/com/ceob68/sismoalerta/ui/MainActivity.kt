package com.ceob68.sismoalerta.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ceob68.sismoalerta.domain.service.SismoSensorService
import com.ceob68.sismoalerta.ui.screens.MainScreen
import com.ceob68.sismoalerta.ui.theme.SismoAlertaTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            val notificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
            
            if (fineLocationGranted || coarseLocationGranted) {
                Timber.d("Permisos de ubicación otorgados")
                getLastLocation()
            }
            
            if (!notificationGranted) {
                Timber.w("Permisos de notificación no otorgados")
            }
        }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Timber.d("MainActivity creada")
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        requestPermissions()
        SismoSensorService.startService(this)
        
        setContent {
            SismoAlertaTheme {
                MainScreen()
            }
        }
    }
    
    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.WAKE_LOCK
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val permissionsNeeded = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            getLastLocation()
        }
    }
    
    private fun getLastLocation() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        Timber.d("Ubicación obtenida: ${location.latitude}, ${location.longitude}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener ubicación")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("MainActivity destruida")
    }
}