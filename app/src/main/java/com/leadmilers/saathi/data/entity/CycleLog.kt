package com.leadmilers.saathi.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cycle_log")
data class CycleLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val cycleLength: Int,
    val flowIntensity: String,
    val notes: String = ""
)
