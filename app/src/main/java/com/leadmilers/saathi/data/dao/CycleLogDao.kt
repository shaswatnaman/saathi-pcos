package com.leadmilers.saathi.data.dao

import androidx.room.*
import com.leadmilers.saathi.data.entity.CycleLog
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CycleLog): Long

    @Update
    suspend fun update(log: CycleLog)

    @Delete
    suspend fun delete(log: CycleLog)

    @Query("SELECT * FROM cycle_log ORDER BY date DESC")
    fun getAll(): Flow<List<CycleLog>>

    @Query("SELECT * FROM cycle_log WHERE id = :id")
    suspend fun getById(id: Long): CycleLog?

    @Query("SELECT * FROM cycle_log ORDER BY date DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<CycleLog>>

    @Query("SELECT * FROM cycle_log WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun getInRange(from: Long, to: Long): Flow<List<CycleLog>>

    @Query("DELETE FROM cycle_log")
    suspend fun deleteAll()
}
