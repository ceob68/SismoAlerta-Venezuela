package com.ceob68.sismoalerta.ui.components

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ceob68.sismoalerta.data.model.SismoModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import timber.log.Timber

@Composable
fun SismoMapView(
    sismos: List<SismoModel>,
    onSismoSelected: (SismoModel) -> Unit,
    selectedSismo: SismoModel? = null
) {
    val context = LocalContext.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    
    // Configuración inicial de OSMDroid
    LaunchedEffect(Unit) {
        try {
            // ⚠️ CRÍTICO: Evita bloqueos en los servidores de OpenStreetMap configurando un User-Agent único
            // Sin esto, los tiles se descargan muy lentamente o no se cargan en absoluto
            Configuration.getInstance().userAgentValue = context.packageName
            
            // Cargar preferencias con SharedPreferences estándar (no deprecado)
            // Reemplaza android.preference.PreferenceManager (deprecado desde API 29)
            Configuration.getInstance().load(
                context,
                context.getSharedPreferences("osmdroid_prefs", android.content.Context.MODE_PRIVATE)
            )
            
            Timber.d("Configuración de OSMDroid inicializada correctamente")
        } catch (e: Exception) {
            Timber.e(e, "Error al inicializar OSMDroid")
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    mapViewRef.value = this
                    
                    try {
                        // Configurar origen de datos (Mapnik es el estándar de OpenStreetMap)
                        setTileSource(TileSourceFactory.MAPNIK)
                        
                        // Habilitar gestos multitáctiles (zoom con dos dedos)
                        setMultiTouchControls(true)
                        
                        // Configurar posición inicial centrada en Venezuela
                        controller.setZoom(6.5)
                        val centroVenezuela = GeoPoint(7.0, -65.0)
                        controller.setCenter(centroVenezuela)
                        
                        Timber.d("MapView de OSMDroid creada exitosamente")
                    } catch (e: Exception) {
                        Timber.e(e, "Error al crear MapView")
                    }
                }
            },
            update = { mapView ->
                try {
                    // Limpiar marcadores antiguos antes de redibujar
                    mapView.overlays.clear()
                    
                    // Iterar sobre la lista de sismos
                    sismos.forEach { sismo ->
                        val sismoPoint = GeoPoint(sismo.latitude, sismo.longitude)
                        
                        val marker = Marker(mapView).apply {
                            position = sismoPoint
                            title = "Mag: ${sismo.magnitude}"
                            subDescription = sismo.place
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            
                            // Ventana de información personalizada
                            infoWindow = SismoInfoWindow(mapView, sismo, onSismoSelected)
                        }
                        
                        mapView.overlays.add(marker)
                    }
                    
                    // Forzar redibujado del mapa
                    mapView.invalidate()
                    
                    Timber.d("${sismos.size} marcadores agregados al mapa OSM")
                } catch (e: Exception) {
                    Timber.e(e, "Error al actualizar marcadores en mapa")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Card de información del sismo seleccionado (overlay de Compose)
        // Arquitectura híbrida elegante: Interceptamos onOpen() del InfoWindow nativo
        // para actualizar el estado de Compose y renderizar la tarjeta en Compose
        if (selectedSismo != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                SismoInfoContent(
                    sismo = selectedSismo,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

/**
 * Ventana de información personalizada para marcadores OSMDroid
 * Intercepta el onOpen para actualizar el estado de Compose
 * Esta es una genialidad arquitectónica: evita pelear con XML inflados nativos
 */
class SismoInfoWindow(
    mapView: MapView,
    private val sismo: SismoModel,
    private val onSismoSelected: (SismoModel) -> Unit
) : org.osmdroid.views.overlay.InfoWindow(0, mapView) {
    
    override fun onOpen(item: Any?) {
        // Actualiza el estado de Compose cuando se abre el marcador
        onSismoSelected(sismo)
        Timber.d("InfoWindow abierto para sismo: ${sismo.place}")
    }
    
    override fun onClose() {
        Timber.d("InfoWindow cerrado")
    }
}

@Composable
fun SismoInfoContent(
    sismo: SismoModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Magnitud",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .background(
                        color = ComposeColor(
                            when {
                                sismo.magnitude < 3.0 -> Color.GREEN
                                sismo.magnitude < 5.0 -> Color.YELLOW
                                else -> Color.RED
                            }
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "%.1f".format(sismo.magnitude),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ComposeColor.Black
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Ubicación",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                sismo.place.take(40),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Profundidad",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "%.1f km".format(sismo.depth),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Hora (HLV)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                sismo.formattedDateTime,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        if (sismo.tsunami == 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "⚠️ Riesgo de Tsunami",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}