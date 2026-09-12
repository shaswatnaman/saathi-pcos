package com.leadmilers.saathi.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "risk_assessment")
data class RiskAssessment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val totalScore: Int,
    val cycleScore: Int,
    val acneScore: Int,
    val fatigueScore: Int,
    val physicalScore: Int,
    val riskLevel: String
)
