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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ceob68.sismoalerta.data.model.SismoModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import timber.log.Timber

@Composable
fun SismoMapView(
    sismos: List<SismoModel>,
    onSismoSelected: (SismoModel) -> Unit,
    selectedSismo: SismoModel? = null
) {
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    mapViewRef.value = this
                    onCreate(null)
                    getMapAsync { googleMap ->
                        try {
                            val venezuelaCenter = LatLng(6.5, -66.5)
                            googleMap.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(venezuelaCenter, 6f)
                            )
                            
                            sismos.forEach { sismo ->
                                val markerColor = when {
                                    sismo.magnitude < 3.0 -> Color.GREEN
                                    sismo.magnitude < 5.0 -> Color.YELLOW
                                    else -> Color.RED
                                }
                                
                                val marker = googleMap.addMarker(
                                    MarkerOptions()
                                        .position(LatLng(sismo.latitude, sismo.longitude))
                                        .title("Mag: ${sismo.magnitude}")
                                        .snippet(sismo.place)
                                )
                                
                                marker?.tag = sismo
                            }
                            
                            googleMap.setOnMarkerClickListener { marker ->
                                val sismo = marker.tag as? SismoModel
                                if (sismo != null) {
                                    onSismoSelected(sismo)
                                    true
                                } else {
                                    false
                                }
                            }
                            
                            Timber.d("${sismos.size} marcadores agregados al mapa")
                        } catch (e: Exception) {
                            Timber.e(e, "Error al configurar el mapa")
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
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