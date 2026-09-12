package com.leadmilers.saathi.data.dao

import androidx.room.*
import com.leadmilers.saathi.data.entity.HealthEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HealthEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<HealthEntry>)

    @Update
    suspend fun update(entry: HealthEntry)

    @Delete
    suspend fun delete(entry: HealthEntry)

    @Query("DELETE FROM health_entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM health_entries ORDER BY timestamp DESC")
    fun getAll(): Flow<List<HealthEntry>>

    @Query("SELECT * FROM health_entries WHERE moduleId = :moduleId ORDER BY timestamp DESC")
    fun getByModule(moduleId: String): Flow<List<HealthEntry>>

    @Query("SELECT * FROM health_entries WHERE moduleId = :moduleId AND entryType = :entryType ORDER BY timestamp DESC LIMIT :limit")
    fun getByType(moduleId: String, entryType: String, limit: Int = 30): Flow<List<HealthEntry>>

    @Query("SELECT * FROM health_entries WHERE moduleId = :moduleId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentByModule(moduleId: String, limit: Int = 30): List<HealthEntry>

    @Query("SELECT * FROM health_entries WHERE moduleId = :moduleId AND entryType = :entryType ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestByType(moduleId: String, entryType: String): HealthEntry?
}
