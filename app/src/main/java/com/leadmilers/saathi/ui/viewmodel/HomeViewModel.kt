package com.leadmilers.saathi.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leadmilers.saathi.SaathiApp
import com.leadmilers.saathi.data.entity.HealthEntry
import com.leadmilers.saathi.data.entity.RiskAssessment
import com.leadmilers.saathi.data.entity.SymptomLog
import com.leadmilers.saathi.data.entity.CycleLog
import com.leadmilers.saathi.sensor.HealthConnectHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val latestRisk: RiskAssessment? = null,
    val recentSymptomLogs: List<SymptomLog> = emptyList(),
    val recentCycleLogs: List<CycleLog> = emptyList(),
    val isRefreshing: Boolean = false,
    val todaySteps: Long? = null,
    val healthConnectAvailable: Boolean = false
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as SaathiApp).repository

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.latestRiskAssessment,
                repo.recentSymptomLogs(30),
                repo.recentCycleLogs(30)
            ) { risk, symptoms, cycles ->
                _uiState.value.copy(
                    latestRisk        = risk,
                    recentSymptomLogs = symptoms,
                    recentCycleLogs   = cycles
                )
            }.collect { _uiState.value = it }
        }
        // Read Health Connect steps on launch (graceful — stays null if HC not available)
        viewModelScope.launch {
            val hcAvailable = HealthConnectHelper.isAvailable(app)
            if (hcAvailable) {
                val client = HealthConnectHelper.getClient(app)
                if (client != null) {
                    val steps = HealthConnectHelper.readTodaySteps(client)
                    _uiState.update { it.copy(todaySteps = steps, healthConnectAvailable = true) }
                }
            }
        }
    }

    val partnerNotes = repo.healthEntriesByModule("partner")

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            // Recalculate risk from latest data and insert new assessment
            val latestSymptom = repo.getLatestSymptomLog()
            val latestCycle = repo.recentCycleLogs(1).firstOrNull()?.firstOrNull()
            if (latestSymptom != null && latestCycle != null) {
                val newRisk = com.leadmilers.saathi.ml.RiskScorer.calculateRisk(latestCycle, latestSymptom)
                repo.insertRiskAssessment(newRisk)
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
