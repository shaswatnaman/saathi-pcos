package com.leadmilers.saathi

import android.app.Application
import com.leadmilers.saathi.companion.NearbyManager
import com.leadmilers.saathi.data.db.SaathiDatabase
import com.leadmilers.saathi.data.repository.SaathiRepository
import com.leadmilers.saathi.prefs.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SaathiApp : Application() {
    val database by lazy { SaathiDatabase.getInstance(this) }
    val repository by lazy {
        SaathiRepository.getInstance(
            database.cycleLogDao(),
            database.symptomLogDao(),
            database.riskAssessmentDao(),
            database.healthEntryDao(),
            database.healthModuleDao()
        )
    }
    val userPrefs by lazy { UserPrefs.getInstance(this) }
    val nearbyManager by lazy { NearbyManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            repository.seedDefaultModules()
        }
    }
}
