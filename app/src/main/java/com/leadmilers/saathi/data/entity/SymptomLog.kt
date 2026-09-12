package com.leadmilers.saathi.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "symptom_log")
data class SymptomLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val fatigue: Int,
    val acneScore: Float,
    val voiceEnergyScore: Float,
    val weight: Float,
    val waistHip: Float,
    // PCOS symptom flags (added v2)
    @ColumnInfo(defaultValue = "0") val skinDarkening: Boolean = false,
    @ColumnInfo(defaultValue = "0") val weightGain: Boolean = false,
    @ColumnInfo(defaultValue = "0") val hairIssues: Boolean = false
)
