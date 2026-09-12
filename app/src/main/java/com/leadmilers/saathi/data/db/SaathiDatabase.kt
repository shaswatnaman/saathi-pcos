package com.leadmilers.saathi.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.leadmilers.saathi.data.dao.*
import com.leadmilers.saathi.data.entity.*

@Database(
    entities = [
        CycleLog::class,
        SymptomLog::class,
        RiskAssessment::class,
        HealthEntry::class,
        HealthModule::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SaathiDatabase : RoomDatabase() {
    abstract fun cycleLogDao(): CycleLogDao
    abstract fun symptomLogDao(): SymptomLogDao
    abstract fun riskAssessmentDao(): RiskAssessmentDao
    abstract fun healthEntryDao(): HealthEntryDao
    abstract fun healthModuleDao(): HealthModuleDao

    companion object {
        @Volatile private var INSTANCE: SaathiDatabase? = null

        fun getInstance(context: Context): SaathiDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SaathiDatabase::class.java,
                    "saathi.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
    }
}
