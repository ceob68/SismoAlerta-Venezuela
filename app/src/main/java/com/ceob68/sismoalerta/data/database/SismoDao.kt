package com.ceob68.sismoalerta.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SismoDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSismo(sismo: SismoEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSismos(sismos: List<SismoEntity>)
    
    @Update
    suspend fun updateSismo(sismo: SismoEntity)
    
    @Delete
    suspend fun deleteSismo(sismo: SismoEntity)
    
    @Query("SELECT * FROM sismos ORDER BY timeMillis DESC")
    fun getAllSismos(): Flow<List<SismoEntity>>
    
    @Query("SELECT * FROM sismos WHERE magnitude >= :minMagnitude ORDER BY timeMillis DESC")
    fun getSismosByMagnitude(minMagnitude: Double): Flow<List<SismoEntity>>
    
    @Query("""
        SELECT * FROM sismos 
        WHERE timeMillis >= :sinceMillis 
        ORDER BY timeMillis DESC
    """)
    fun getSismosSince(sinceMillis: Long): Flow<List<SismoEntity>>
    
    @Query("SELECT * FROM sismos WHERE id = :sismoId")
    suspend fun getSismoById(sismoId: String): SismoEntity?
    
    @Query("""
        SELECT * FROM sismos 
        WHERE magnitude BETWEEN :minMag AND :maxMag 
        ORDER BY timeMillis DESC
    """)
    fun getSismosByMagnitudeRange(minMag: Double, maxMag: Double): Flow<List<SismoEntity>>
    
    @Query("SELECT COUNT(*) FROM sismos")
    suspend fun getSismoCount(): Int
    
    @Query("DELETE FROM sismos WHERE timeMillis < :beforeMillis")
    suspend fun deleteSismosOlderThan(beforeMillis: Long)
    
    @Query("DELETE FROM sismos")
    suspend fun clearAll()
}