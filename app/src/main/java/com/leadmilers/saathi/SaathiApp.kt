package com.leadmilers.saathi

import android.app.Application
import com.leadmilers.saathi.data.db.SaathiDatabase
import com.leadmilers.saathi.data.repository.SaathiRepository

class SaathiApp : Application() {
    val database by lazy { SaathiDatabase.getInstance(this) }
    val repository by lazy {
        SaathiRepository.getInstance(
            database.cycleLogDao(),
            database.symptomLogDao(),
            database.riskAssessmentDao()
        )
    }
}
