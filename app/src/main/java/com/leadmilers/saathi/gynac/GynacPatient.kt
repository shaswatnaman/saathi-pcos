package com.leadmilers.saathi.gynac

data class GynacPatient(
    val id: String,
    val name: String,
    val age: Int,
    val diagnosis: String = "PCOS",
    val patientCode: String,
    val lastVisitDays: Int,
    val nextFollowUpDays: Int?,
    val status: GynacStatus,

    // Cycle
    val avgCycleLength: Int,
    val recentCycleLength: Int,
    val cycleTrend: HealthTrend,
    val cycleHistory: List<Int>,          // last 6 cycle lengths

    // Symptoms
    val acneSeverity: String,
    val acneTrend: HealthTrend,
    val acneHistory: List<Float>,         // 0–1, 30 weekly samples
    val fatigueAvg: Float,
    val fatigueTrend: HealthTrend,
    val painAvg: Float,

    // Lifestyle
    val sleepAvg: Float,
    val sleepTrend: HealthTrend,
    val sleepHistory: List<Float>,        // hours, 30 samples

    // Medications
    val primaryMedication: String,
    val primaryMedDose: String,
    val medications: List<GynacMedication>,

    // Clinical
    val clinicalNotes: List<GynacNote>,
    val alerts: List<GynacAlert>,
    val dataCompleteness: Int,
    val lastUpdateHours: Int,
)

data class GynacMedication(
    val name: String,
    val dose: String,
    val frequency: String,
    val startDate: String,
    val endDate: String? = null,
    val active: Boolean,
    val prescribedBy: String = "Dr. Mehta",
    val notes: String = "",
)

data class GynacNote(
    val date: String,
    val type: NoteType,
    val content: String,
    val source: DataSource,
)

data class GynacAlert(
    val message: String,
    val severity: AlertSev,
    val daysAgo: Int,
)

enum class GynacStatus { STABLE, MONITORING, FOLLOW_UP, NEEDS_REVIEW }
enum class HealthTrend { IMPROVING, STABLE, WORSENING, VARIABLE }
enum class AlertSev { INFO, WARNING, URGENT }
enum class NoteType { CONSULTATION, ASSESSMENT, PLAN, PATIENT_NOTE, FOLLOWUP }
enum class DataSource { PATIENT, CLINICIAN, SYSTEM }
