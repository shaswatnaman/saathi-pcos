package com.leadmilers.saathi.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_entries")
data class HealthEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val moduleId: String,       // "pcos", "general"
    val entryType: String,      // "acne_score", "cycle_length", "pain_level", "mood", etc.
    val numericValue: Double? = null,
    val textValue: String? = null,
    val confidence: Float? = null,
    val sourceType: String = "self_report", // "self_report" | "camera" | "microphone" | "manual"
    val timestamp: Long,
    val metadata: String? = null  // JSON blob: grooming flags, device info, etc.
)
