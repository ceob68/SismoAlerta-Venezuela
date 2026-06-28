package com.ceob68.sismoalerta.data.api

import com.ceob68.sismoalerta.data.model.SismoResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface SismoApiService {
    
    /**
     * Obtiene los sismos recientes en Venezuela desde la USGS FDSN API
     * 
     * @param minLatitude Latitud mínima del bbox de Venezuela: 0.5
     * @param maxLatitude Latitud máxima del bbox de Venezuela: 13.0
     * @param minLongitude Longitud mínima del bbox de Venezuela: -73.5
     * @param maxLongitude Longitud máxima del bbox de Venezuela: -59.5
     * @param minMagnitude Magnitud mínima a considerar
     * @param orderBy Orden de los resultados (time / magnitude)
     * @param format Formato de respuesta (geojson)
     * @param limit Límite de registros
     */
    @GET("query")
    suspend fun getEarthquakesInVenezuela(
        @Query("minlatitude") minLatitude: Double = VENEZUELA_MIN_LAT,
        @Query("maxlatitude") maxLatitude: Double = VENEZUELA_MAX_LAT,
        @Query("minlongitude") minLongitude: Double = VENEZUELA_MIN_LON,
        @Query("maxlongitude") maxLongitude: Double = VENEZUELA_MAX_LON,
        @Query("minmagnitude") minMagnitude: Double = 2.0,
        @Query("orderby") orderBy: String = "time",
        @Query("format") format: String = "geojson",
        @Query("limit") limit: Int = 300,
        @Query("starttime") starttime: String? = null,
        @Query("endtime") endtime: String? = null
    ): SismoResponse

    companion object {
        // Bounding Box para Venezuela (estricto)
        const val VENEZUELA_MIN_LAT = 0.5
        const val VENEZUELA_MAX_LAT = 13.0
        const val VENEZUELA_MIN_LON = -73.5
        const val VENEZUELA_MAX_LON = -59.5
        
        const val BASE_URL = "https://earthquake.usgs.gov/fdsnws/event/1/"
    }
}