package com.leadmilers.saathi

import android.app.Application
import com.leadmilers.saathi.ai.LocalAiManager
import com.leadmilers.saathi.companion.LanSyncManager
import com.leadmilers.saathi.data.db.SaathiDatabase
import com.leadmilers.saathi.data.repository.SaathiRepository
import com.leadmilers.saathi.demo.DemoDataSeeder
import com.leadmilers.saathi.companion.SyncPacket
import com.leadmilers.saathi.gynac.GynacPatient
import com.leadmilers.saathi.prefs.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SaathiApp : Application() {
    /** Holds the most-recently synced live patient so GynacPatientProfileScreen can display it. */
    @Volatile var currentLivePatient: GynacPatient? = null
    /** Raw sync packet for the live patient — gives gynac access to full log history. */
    @Volatile var currentLiveSyncPacket: SyncPacket? = null
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
    val lanSyncManager by lazy { LanSyncManager.getInstance(this) }
    val localAiManager by lazy { LocalAiManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            repository.seedDefaultModules()
            if (!userPrefs.demoDataSeeded) {
                DemoDataSeeder.seed(repository)
                userPrefs.demoDataSeeded = true
            }
        }
    }
}
