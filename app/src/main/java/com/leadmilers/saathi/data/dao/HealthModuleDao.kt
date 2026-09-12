package com.leadmilers.saathi.data.dao

import androidx.room.*
import com.leadmilers.saathi.data.entity.HealthModule
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthModuleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(module: HealthModule)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(modules: List<HealthModule>)

    @Update
    suspend fun update(module: HealthModule)

    @Query("SELECT * FROM health_modules ORDER BY addedDate ASC")
    fun getAll(): Flow<List<HealthModule>>

    @Query("SELECT * FROM health_modules WHERE isActive = 1 ORDER BY addedDate ASC")
    fun getActive(): Flow<List<HealthModule>>

    @Query("SELECT COUNT(*) FROM health_modules")
    suspend fun count(): Int
}
