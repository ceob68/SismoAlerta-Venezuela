package com.ceob68.sismoalerta.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.ceob68.sismoalerta.data.model.SismoModel
import com.ceob68.sismoalerta.ui.viewmodel.SismoViewModel
import com.ceob68.sismoalerta.ui.viewmodel.UiState
import com.ceob68.sismoalerta.ui.viewmodel.SismoAppState
import com.ceob68.sismoalerta.ui.components.SismoCard
import com.ceob68.sismoalerta.ui.components.SismoMapView
import timber.log.Timber

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: SismoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    var selectedSismo by remember { mutableStateOf<SismoModel?>(null) }
    var minMagnitudeFilter by remember { mutableStateOf(2.0f) }
    
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    
    LaunchedEffect(Unit) {
        if (!locationPermissions.allPermissionsGranted) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SismoAlerta Venezuela",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Mapa", fontSize = 14.sp) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Lista", fontSize = 14.sp) }
                )
            }
            
            when (selectedTabIndex) {
                0 -> MapTabContent(
                    uiState = uiState,
                    onSismoSelected = { sismo ->
                        selectedSismo = sismo
                        viewModel.selectSismo(sismo)
                    },
                    selectedSismo = selectedSismo,
                    onRefresh = { viewModel.refreshSismos() }
                )
                1 -> ListTabContent(
                    uiState = uiState,
                    sismos = uiState.let { state ->
                        when (state.uiState) {
                            is UiState.Success -> state.filteredSismos
                            else -> emptyList()
                        }
                    },
                    onSismoSelected = { sismo ->
                        selectedSismo = sismo
                        viewModel.selectSismo(sismo)
                    },
                    minMagnitudeFilter = minMagnitudeFilter,
                    onMagnitudeFilterChange = { newValue ->
                        minMagnitudeFilter = newValue
                        viewModel.filterByMagnitude(newValue.toDouble())
                    },
                    onRefresh = { viewModel.refreshSismos() },
                    distanceToSismos = uiState.distanceToSismos,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun MapTabContent(
    uiState: SismoAppState,
    onSismoSelected: (SismoModel) -> Unit,
    selectedSismo: SismoModel?,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (uiState.uiState) {
            UiState.Loading -> LoadingScreen()
            is UiState.Success -> SismoMapView(
                sismos = uiState.filteredSismos,
                onSismoSelected = onSismoSelected,
                selectedSismo = selectedSismo
            )
            is UiState.Error -> ErrorScreen(
                errorMessage = (uiState.uiState as UiState.Error).message,
                onRetry = onRefresh
            )
            UiState.Empty -> EmptyScreen()
        }
    }
}

@Composable
fun ListTabContent(
    uiState: SismoAppState,
    sismos: List<SismoModel>,
    onSismoSelected: (SismoModel) -> Unit,
    minMagnitudeFilter: Float,
    onMagnitudeFilterChange: (Float) -> Unit,
    onRefresh: () -> Unit,
    distanceToSismos: Map<String, Double>,
    viewModel: SismoViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    "Magnitud mínima: %.1f".format(minMagnitudeFilter.toDouble()),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = minMagnitudeFilter,
                    onValueChange = onMagnitudeFilterChange,
                    valueRange = 2.0f..7.0f,
                    steps = 9,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
        
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when (uiState.uiState) {
                UiState.Loading -> LoadingScreen()
                is UiState.Success -> {
                    if (sismos.isEmpty()) {
                        EmptyScreen()
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item { Spacer(modifier = Modifier.height(4.dp)) }
                            items(
                                items = sismos,
                                key = { it.id }
                            ) { sismo ->
                                SismoCard(
                                    sismo = sismo,
                                    distance = distanceToSismos[sismo.id],
                                    onClick = { onSismoSelected(sismo) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(12.dp)) }
                        }
                    }
                }
                is UiState.Error -> ErrorScreen(
                    errorMessage = (uiState.uiState as UiState.Error).message,
                    onRetry = onRefresh
                )
                UiState.Empty -> EmptyScreen()
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Cargando sismos...",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ErrorScreen(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "❌ Error",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                errorMessage,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
fun EmptyScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "📍 Sin datos",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "No hay sismos registrados en este momento",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}