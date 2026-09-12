package com.leadmilers.saathi.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_modules")
data class HealthModule(
    @PrimaryKey val id: String,
    val displayName: String,
    val description: String,
    val isActive: Boolean,
    val isComingSoon: Boolean = false,
    val addedDate: Long = System.currentTimeMillis()
)
