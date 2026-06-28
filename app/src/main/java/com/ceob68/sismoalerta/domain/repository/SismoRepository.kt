package com.ceob68.sismoalerta.domain.repository

import com.ceob68.sismoalerta.data.api.SismoApiService
import com.ceob68.sismoalerta.data.database.SismoDao
import com.ceob68.sismoalerta.data.database.SismoEntity
import com.ceob68.sismoalerta.data.model.SismoFeature
import com.ceob68.sismoalerta.data.model.SismoModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class SismoResult<out T> {
    data class Success<T>(val data: T) : SismoResult<T>()
    data class Error(val exception: Throwable) : SismoResult<Nothing>()
    object Loading : SismoResult<Nothing>()
}

@Singleton
class SismoRepository @Inject constructor(
    private val sismoApiService: SismoApiService,
    private val sismoDao: SismoDao
) {
    
    fun getAllSismos(): Flow<SismoResult<List<SismoModel>>> = flow {
        emit(SismoResult.Loading)
        
        try {
            val response = withContext(Dispatchers.IO) {
                sismoApiService.getEarthquakesInVenezuela()
            }
            
            val sismos = response.features.mapNotNull { feature ->
                featureToSismoModel(feature)
            }
            
            withContext(Dispatchers.IO) {
                sismoDao.insertSismos(sismos.map { it.toEntity() })
            }
            
            Timber.d("${sismos.size} sismos obtenidos de la API y guardados")
            emit(SismoResult.Success(sismos))
            
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener sismos de la API, usando caché")
            
            sismoDao.getAllSismos()
                .map { entities ->
                    if (entities.isEmpty()) {
                        SismoResult.Error(e)
                    } else {
                        SismoResult.Success(entities.map { it.toModel() })
                    }
                }
                .catch { cacheError ->
                    Timber.e(cacheError, "Error al leer caché local")
                    emit(SismoResult.Error(cacheError))
                }
                .collect { emit(it) }
        }
    }
    
    fun getSismosByMinMagnitude(minMagnitude: Double): Flow<SismoResult<List<SismoModel>>> = flow {
        emit(SismoResult.Loading)
        
        try {
            val response = withContext(Dispatchers.IO) {
                sismoApiService.getEarthquakesInVenezuela(minMagnitude = minMagnitude)
            }
            
            val sismos = response.features.mapNotNull { feature ->
                featureToSismoModel(feature)
            }.filter { it.magnitude >= minMagnitude }
            
            withContext(Dispatchers.IO) {
                sismoDao.insertSismos(sismos.map { it.toEntity() })
            }
            
            emit(SismoResult.Success(sismos))
            
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener sismos por magnitud")
            
            sismoDao.getSismosByMagnitude(minMagnitude)
                .map { entities ->
                    if (entities.isEmpty()) {
                        SismoResult.Error(e)
                    } else {
                        SismoResult.Success(entities.map { it.toModel() })
                    }
                }
                .catch { emit(SismoResult.Error(it)) }
                .collect { emit(it) }
        }
    }
    
    fun getSismosByMagnitudeRange(minMag: Double, maxMag: Double): Flow<SismoResult<List<SismoModel>>> = flow {
        emit(SismoResult.Loading)
        
        try {
            val response = withContext(Dispatchers.IO) {
                sismoApiService.getEarthquakesInVenezuela(minMagnitude = minMag)
            }
            
            val sismos = response.features.mapNotNull { feature ->
                featureToSismoModel(feature)
            }.filter { it.magnitude in minMag..maxMag }
            
            withContext(Dispatchers.IO) {
                sismoDao.insertSismos(sismos.map { it.toEntity() })
            }
            
            emit(SismoResult.Success(sismos))
            
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener sismos por rango de magnitud")
            
            sismoDao.getSismosByMagnitudeRange(minMag, maxMag)
                .map { entities ->
                    if (entities.isEmpty()) {
                        SismoResult.Error(e)
                    } else {
                        SismoResult.Success(entities.map { it.toModel() })
                    }
                }
                .catch { emit(SismoResult.Error(it)) }
                .collect { emit(it) }
        }
    }
    
    fun getSismosSince(days: Int): Flow<SismoResult<List<SismoModel>>> = flow {
        emit(SismoResult.Loading)
        
        try {
            val sinceMillis = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            
            sismoDao.getSismosSince(sinceMillis)
                .map { entities ->
                    SismoResult.Success(entities.map { it.toModel() })
                }
                .catch { emit(SismoResult.Error(it)) }
                .collect { emit(it) }
                
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener sismos recientes")
            emit(SismoResult.Error(e))
        }
    }
    
    suspend fun getSismoById(sismoId: String): SismoModel? = withContext(Dispatchers.IO) {
        try {
            sismoDao.getSismoById(sismoId)?.toModel()
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener sismo por ID: $sismoId")
            null
        }
    }
    
    suspend fun getSismoCount(): Int = withContext(Dispatchers.IO) {
        try {
            sismoDao.getSismoCount()
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener conteo de sismos")
            0
        }
    }
    
    suspend fun clearOldSismos(hoursAgo: Int) = withContext(Dispatchers.IO) {
        try {
            val beforeMillis = System.currentTimeMillis() - (hoursAgo * 60 * 60 * 1000L)
            sismoDao.deleteSismosOlderThan(beforeMillis)
            Timber.d("Sismos antiguos ($hoursAgo horas) eliminados")
        } catch (e: Exception) {
            Timber.e(e, "Error al eliminar sismos antiguos")
        }
    }
    
    suspend fun clearAllSismos() = withContext(Dispatchers.IO) {
        try {
            sismoDao.clearAll()
            Timber.d("Caché de sismos vaciada completamente")
        } catch (e: Exception) {
            Timber.e(e, "Error al limpiar caché")
        }
    }
    
    private fun featureToSismoModel(feature: SismoFeature): SismoModel? = try {
        val coords = feature.geometry.coordinates
        val props = feature.properties
        
        SismoModel(
            id = feature.id,
            magnitude = props.magnitude ?: 0.0,
            place = props.place,
            latitude = coords.getOrNull(1) ?: 0.0,
            longitude = coords.getOrNull(0) ?: 0.0,
            depth = coords.getOrNull(2) ?: 0.0,
            timeMillis = props.time,
            url = props.url,
            tsunami = props.tsunami
        )
    } catch (e: Exception) {
        Timber.e(e, "Error al convertir Feature a SismoModel")
        null
    }
    
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Radio de la Tierra en km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        
        val c = 2 * Math.asin(Math.sqrt(a))
        return R * c
    }
    
    suspend fun getSismosCercanos(
        userLat: Double,
        userLon: Double,
        radioKm: Double = 500.0
    ): List<SismoModel> = withContext(Dispatchers.IO) {
        try {
            val count = sismoDao.getSismoCount()
            if (count == 0) return@withContext emptyList()
            
            sismoDao.getAllSismos().map { entities ->
                entities.map { entity -> entity.toModel() }
                    .filter { sismo ->
                        calculateDistance(
                            userLat, userLon,
                            sismo.latitude, sismo.longitude
                        ) <= radioKm
                    }
                    .sortedBy { it.timeMillis }
                    .reversed()
            }.collect { it }
            
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener sismos cercanos")
            emptyList()
        }
    }
}