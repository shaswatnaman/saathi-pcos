package com.leadmilers.saathi.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.leadmilers.saathi.data.dao.CycleLogDao
import com.leadmilers.saathi.data.dao.RiskAssessmentDao
import com.leadmilers.saathi.data.dao.SymptomLogDao
import com.leadmilers.saathi.data.entity.CycleLog
import com.leadmilers.saathi.data.entity.RiskAssessment
import com.leadmilers.saathi.data.entity.SymptomLog

@Database(
    entities = [CycleLog::class, SymptomLog::class, RiskAssessment::class],
    version = 2,
    exportSchema = false
)
abstract class SaathiDatabase : RoomDatabase() {
    abstract fun cycleLogDao(): CycleLogDao
    abstract fun symptomLogDao(): SymptomLogDao
    abstract fun riskAssessmentDao(): RiskAssessmentDao

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
