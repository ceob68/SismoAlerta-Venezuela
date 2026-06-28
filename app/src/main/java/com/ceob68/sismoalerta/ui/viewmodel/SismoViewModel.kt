package com.ceob68.sismoalerta.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ceob68.sismoalerta.data.model.SismoModel
import com.ceob68.sismoalerta.domain.repository.SismoRepository
import com.ceob68.sismoalerta.domain.repository.SismoResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class UiState {
    object Loading : UiState()
    data class Success(val sismos: List<SismoModel>) : UiState()
    data class Error(val message: String) : UiState()
    object Empty : UiState()
}

data class SismoFilter(
    val minMagnitude: Double = 2.0,
    val maxMagnitude: Double = 10.0,
    val sortBy: SortType = SortType.TIME_DESC
)

enum class SortType {
    TIME_DESC, TIME_ASC, MAGNITUDE_DESC, MAGNITUDE_ASC
}

data class SismoAppState(
    val sismos: List<SismoModel> = emptyList(),
    val filteredSismos: List<SismoModel> = emptyList(),
    val uiState: UiState = UiState.Loading,
    val filter: SismoFilter = SismoFilter(),
    val userLatitude: Double? = null,
    val userLongitude: Double? = null,
    val selectedSismo: SismoModel? = null,
    val isRefreshing: Boolean = false,
    val distanceToSismos: Map<String, Double> = emptyMap()
)

@HiltViewModel
class SismoViewModel @Inject constructor(
    private val sismoRepository: SismoRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<SismoAppState>(SismoAppState())
    val uiState: StateFlow<SismoAppState> = _uiState.asStateFlow()
    
    init {
        Timber.d("SismoViewModel inicializado")
        loadAllSismos()
    }
    
    fun loadAllSismos() {
        viewModelScope.launch {
            _uiState.update { it.copy(uiState = UiState.Loading) }
            
            sismoRepository.getAllSismos().collect { result ->
                when (result) {
                    is SismoResult.Loading -> {
                        _uiState.update { it.copy(uiState = UiState.Loading) }
                    }
                    is SismoResult.Success -> {
                        val sismos = result.data
                        _uiState.update { state ->
                            state.copy(
                                sismos = sismos,
                                filteredSismos = filterAndSortSismos(sismos, state.filter),
                                uiState = if (sismos.isEmpty()) UiState.Empty else UiState.Success(sismos),
                                isRefreshing = false
                            )
                        }
                        Timber.d("${sismos.size} sismos cargados")
                    }
                    is SismoResult.Error -> {
                        Timber.e(result.exception, "Error al cargar sismos")
                        _uiState.update { state ->
                            state.copy(
                                uiState = UiState.Error(result.exception.localizedMessage ?: "Error desconocido"),
                                isRefreshing = false
                            )
                        }
                    }
                }
            }
        }
    }
    
    fun filterByMagnitude(minMagnitude: Double) {
        _uiState.update { state ->
            val newFilter = state.filter.copy(minMagnitude = minMagnitude)
            state.copy(
                filter = newFilter,
                filteredSismos = filterAndSortSismos(state.sismos, newFilter)
            )
        }
    }
    
    fun setSortType(sortType: SortType) {
        _uiState.update { state ->
            val newFilter = state.filter.copy(sortBy = sortType)
            state.copy(
                filter = newFilter,
                filteredSismos = filterAndSortSismos(state.sismos, newFilter)
            )
        }
    }
    
    private fun filterAndSortSismos(
        sismos: List<SismoModel>,
        filter: SismoFilter
    ): List<SismoModel> {
        return sismos
            .filter { it.magnitude in filter.minMagnitude..filter.maxMagnitude }
            .sortedWith(
                when (filter.sortBy) {
                    SortType.TIME_DESC -> compareBy<SismoModel> { it.timeMillis }.reversed()
                    SortType.TIME_ASC -> compareBy { it.timeMillis }
                    SortType.MAGNITUDE_DESC -> compareBy<SismoModel> { it.magnitude }.reversed()
                    SortType.MAGNITUDE_ASC -> compareBy { it.magnitude }
                }
            )
    }
    
    fun updateUserLocation(latitude: Double, longitude: Double) {
        _uiState.update { state ->
            val distances = mutableMapOf<String, Double>()
            state.sismos.forEach { sismo ->
                val distance = sismoRepository.calculateDistance(
                    latitude, longitude,
                    sismo.latitude, sismo.longitude
                )
                distances[sismo.id] = distance
            }
            
            state.copy(
                userLatitude = latitude,
                userLongitude = longitude,
                distanceToSismos = distances
            )
        }
    }
    
    fun selectSismo(sismo: SismoModel) {
        _uiState.update { it.copy(selectedSismo = sismo) }
    }
    
    fun clearSelection() {
        _uiState.update { it.copy(selectedSismo = null) }
    }
    
    fun refreshSismos() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadAllSismos()
    }
    
    fun getDistanceToSismo(sismoId: String): Double? {
        return _uiState.value.distanceToSismos[sismoId]
    }
    
    fun getSismosCercanos(radioKm: Double = 500.0): List<SismoModel> {
        val state = _uiState.value
        return if (state.userLatitude != null && state.userLongitude != null) {
            state.filteredSismos.filter { sismo ->
                val distance = state.distanceToSismos[sismo.id] ?: return@filter false
                distance <= radioKm
            }
        } else {
            emptyList()
        }
    }
}