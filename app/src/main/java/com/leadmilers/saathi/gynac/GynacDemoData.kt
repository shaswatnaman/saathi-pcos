package com.leadmilers.saathi.gynac

object GynacDemoData {

    val doctor = GynacDoctor(
        name = "Dr. Priya Mehta",
        credentials = "MD, DGO",
        specialty = "Gynecology & Endocrinology",
        clinic = "Mehta Women's Health Clinic",
        activePatients = 24,
        followUpsToday = 5,
        newUpdates = 3,
    )

    val patients: List<GynacPatient> = listOf(

        // 1. NEEDS REVIEW — classic demo patient, cycle going irregular + acne worsening
        GynacPatient(
            id = "p001", name = "Ananya Sharma", age = 28, patientCode = "SA-000421",
            lastVisitDays = 12, nextFollowUpDays = 6,
            status = GynacStatus.NEEDS_REVIEW,
            avgCycleLength = 38, recentCycleLength = 44, cycleTrend = HealthTrend.WORSENING,
            cycleHistory = listOf(34, 35, 37, 38, 41, 44),
            acneSeverity = "Moderate", acneTrend = HealthTrend.WORSENING,
            acneHistory = listOf(.25f,.28f,.30f,.30f,.32f,.35f,.38f,.40f,.42f,.45f,
                                 .48f,.50f,.52f,.54f,.56f,.58f,.60f,.62f,.64f,.66f,
                                 .68f,.70f,.72f,.73f,.74f,.74f,.75f,.75f,.76f,.76f),
            fatigueAvg = 3.8f, fatigueTrend = HealthTrend.WORSENING, painAvg = 0.3f,
            sleepAvg = 6.1f, sleepTrend = HealthTrend.WORSENING,
            sleepHistory = listOf(7.2f,7.0f,6.9f,6.8f,6.7f,6.6f,6.5f,6.4f,6.3f,6.2f,
                                  6.2f,6.1f,6.0f,6.0f,5.9f,5.9f,5.8f,5.8f,5.7f,5.7f,
                                  5.7f,5.6f,5.6f,5.5f,5.5f,5.5f,5.6f,5.7f,5.8f,6.0f),
            primaryMedication = "Metformin", primaryMedDose = "500 mg",
            medications = listOf(
                GynacMedication("Metformin","500 mg","Once daily","12 Aug 2026",active=true,notes="Review at 3 months"),
                GynacMedication("Folic Acid","5 mg","Once daily","12 Aug 2026",active=true),
            ),
            clinicalNotes = listOf(
                GynacNote("01 Sep 2026", NoteType.CONSULTATION, "Patient reports worsening acne over last 3 weeks. Cycle delayed by 6 days. Sleep quality declining. Continue Metformin, review in 2 weeks.", DataSource.CLINICIAN),
                GynacNote("12 Aug 2026", NoteType.ASSESSMENT, "Initial presentation. Irregular cycles 34–38 days. Mild-moderate acne. PCOS confirmed on ultrasound. Starting Metformin 500 mg.", DataSource.CLINICIAN),
                GynacNote("10 Sep 2026", NoteType.PATIENT_NOTE, "I've been really stressed at work this week. My skin is worse and I couldn't sleep well.", DataSource.PATIENT),
            ),
            alerts = listOf(
                GynacAlert("Cycle variability increased: 34→44 days over 3 months", AlertSev.WARNING, 0),
                GynacAlert("Acne reports ↑ 52% vs last cycle", AlertSev.WARNING, 2),
                GynacAlert("Average sleep ↓ from 7.2h to 5.5h", AlertSev.INFO, 0),
            ),
            dataCompleteness = 87, lastUpdateHours = 2,
        ),

        // 2. FOLLOW_UP — Metformin + Spiro, appointment tomorrow
        GynacPatient(
            id = "p002", name = "Meera Patel", age = 32, patientCode = "SA-000389",
            lastVisitDays = 28, nextFollowUpDays = 1,
            status = GynacStatus.FOLLOW_UP,
            avgCycleLength = 35, recentCycleLength = 33, cycleTrend = HealthTrend.IMPROVING,
            cycleHistory = listOf(42, 40, 38, 36, 35, 33),
            acneSeverity = "Mild", acneTrend = HealthTrend.IMPROVING,
            acneHistory = listOf(.65f,.63f,.60f,.58f,.55f,.52f,.50f,.47f,.44f,.42f,
                                 .40f,.38f,.35f,.33f,.30f,.28f,.27f,.26f,.25f,.24f,
                                 .23f,.22f,.21f,.21f,.20f,.20f,.19f,.19f,.18f,.18f),
            fatigueAvg = 2.8f, fatigueTrend = HealthTrend.IMPROVING, painAvg = 0.2f,
            sleepAvg = 6.8f, sleepTrend = HealthTrend.STABLE,
            sleepHistory = listOf(6.5f,6.6f,6.7f,6.7f,6.8f,6.8f,6.9f,6.9f,7.0f,7.0f,
                                  6.9f,6.9f,6.8f,6.8f,6.8f,6.7f,6.7f,6.8f,6.8f,6.9f,
                                  6.9f,7.0f,7.0f,6.9f,6.8f,6.8f,6.7f,6.7f,6.8f,6.8f),
            primaryMedication = "Metformin + Spiro", primaryMedDose = "500 mg / 25 mg",
            medications = listOf(
                GynacMedication("Metformin","500 mg","Twice daily","15 Jun 2026",active=true),
                GynacMedication("Spironolactone","25 mg","Once daily","16 Jul 2026",active=true,notes="Monitor BP"),
                GynacMedication("OCP (Yasmin)","","Once daily","Mar 2025","May 2026",active=false,notes="Discontinued — patient preference"),
            ),
            clinicalNotes = listOf(
                GynacNote("16 Jul 2026", NoteType.CONSULTATION, "Good response to Metformin over 4 weeks. Adding Spironolactone 25 mg for androgenic symptoms. Cycle shortening to 35 days.", DataSource.CLINICIAN),
                GynacNote("12 Sep 2026", NoteType.PATIENT_NOTE, "Feeling much better overall. Skin is clearing up. Periods more predictable.", DataSource.PATIENT),
            ),
            alerts = listOf(
                GynacAlert("Follow-up appointment tomorrow", AlertSev.INFO, 0),
                GynacAlert("Medication response positive: cycle 42→33 days", AlertSev.INFO, 5),
            ),
            dataCompleteness = 92, lastUpdateHours = 20,
        ),

        // 3. MONITORING — recently started OCP
        GynacPatient(
            id = "p003", name = "Priya Kapoor", age = 24, patientCode = "SA-000512",
            lastVisitDays = 45, nextFollowUpDays = 15,
            status = GynacStatus.MONITORING,
            avgCycleLength = 42, recentCycleLength = 28, cycleTrend = HealthTrend.IMPROVING,
            cycleHistory = listOf(45, 48, 44, 38, 28, 28),
            acneSeverity = "Moderate", acneTrend = HealthTrend.STABLE,
            acneHistory = listOf(.58f,.57f,.55f,.54f,.52f,.51f,.50f,.50f,.49f,.49f,
                                 .48f,.48f,.47f,.47f,.47f,.46f,.46f,.46f,.45f,.45f,
                                 .44f,.44f,.44f,.43f,.43f,.43f,.43f,.42f,.42f,.42f),
            fatigueAvg = 3.2f, fatigueTrend = HealthTrend.STABLE, painAvg = 0.4f,
            sleepAvg = 7.1f, sleepTrend = HealthTrend.STABLE,
            sleepHistory = listOf(7.0f,7.1f,7.2f,7.1f,7.0f,7.0f,7.1f,7.1f,7.2f,7.2f,
                                  7.1f,7.0f,7.0f,7.1f,7.1f,7.2f,7.2f,7.1f,7.0f,7.0f,
                                  7.1f,7.1f,7.2f,7.2f,7.1f,7.0f,7.0f,7.1f,7.1f,7.2f),
            primaryMedication = "OCP (Diane-35)", primaryMedDose = "Standard",
            medications = listOf(
                GynacMedication("Diane-35","Standard","Once daily","30 Jul 2026",active=true,notes="First cycle complete"),
                GynacMedication("Inositol","2g","Twice daily","30 Jul 2026",active=true),
            ),
            clinicalNotes = listOf(
                GynacNote("30 Jul 2026", NoteType.CONSULTATION, "New patient. Highly irregular cycles since menarche. Confirmed PCOS. Starting Diane-35 + Inositol. Review at 3 months.", DataSource.CLINICIAN),
            ),
            alerts = listOf(
                GynacAlert("First 3-month review due in 15 days", AlertSev.INFO, 0),
                GynacAlert("OCP initiated 45 days ago — cycle now regular at 28 days", AlertSev.INFO, 1),
            ),
            dataCompleteness = 74, lastUpdateHours = 36,
        ),

        // 4. STABLE — lifestyle management, doing well
        GynacPatient(
            id = "p004", name = "Divya Singh", age = 26, patientCode = "SA-000298",
            lastVisitDays = 60, nextFollowUpDays = 30,
            status = GynacStatus.STABLE,
            avgCycleLength = 30, recentCycleLength = 29, cycleTrend = HealthTrend.STABLE,
            cycleHistory = listOf(31, 30, 29, 30, 30, 29),
            acneSeverity = "Mild", acneTrend = HealthTrend.STABLE,
            acneHistory = listOf(.22f,.20f,.21f,.20f,.21f,.21f,.20f,.20f,.19f,.19f,
                                 .20f,.20f,.21f,.21f,.20f,.20f,.19f,.20f,.20f,.21f,
                                 .20f,.20f,.19f,.19f,.20f,.20f,.21f,.21f,.20f,.20f),
            fatigueAvg = 2.1f, fatigueTrend = HealthTrend.STABLE, painAvg = 0.15f,
            sleepAvg = 7.6f, sleepTrend = HealthTrend.STABLE,
            sleepHistory = listOf(7.5f,7.6f,7.7f,7.6f,7.5f,7.5f,7.6f,7.7f,7.6f,7.5f,
                                  7.5f,7.6f,7.7f,7.6f,7.5f,7.5f,7.6f,7.7f,7.6f,7.5f,
                                  7.5f,7.6f,7.7f,7.6f,7.5f,7.5f,7.6f,7.7f,7.6f,7.5f),
            primaryMedication = "Lifestyle", primaryMedDose = "—",
            medications = listOf(
                GynacMedication("Inositol","4g","Daily","Jan 2026",active=true),
                GynacMedication("Vitamin D3","2000 IU","Daily","Jan 2026",active=true),
            ),
            clinicalNotes = listOf(
                GynacNote("15 Jul 2026", NoteType.FOLLOWUP, "Patient managing well with lifestyle modifications. Cycle regular 29–31 days. No medication changes needed. Continue monitoring.", DataSource.CLINICIAN),
            ),
            alerts = listOf(GynacAlert("Stable — no significant changes in 60 days", AlertSev.INFO, 0)),
            dataCompleteness = 88, lastUpdateHours = 48,
        ),

        // 5. NEEDS REVIEW — sleep crash, fatigue spike
        GynacPatient(
            id = "p005", name = "Ritika Joshi", age = 30, patientCode = "SA-000467",
            lastVisitDays = 19, nextFollowUpDays = null,
            status = GynacStatus.NEEDS_REVIEW,
            avgCycleLength = 36, recentCycleLength = 39, cycleTrend = HealthTrend.VARIABLE,
            cycleHistory = listOf(33, 38, 34, 40, 36, 39),
            acneSeverity = "Mild", acneTrend = HealthTrend.STABLE,
            acneHistory = listOf(.25f,.25f,.26f,.26f,.25f,.25f,.24f,.24f,.25f,.25f,
                                 .26f,.26f,.25f,.25f,.24f,.24f,.25f,.25f,.26f,.26f,
                                 .25f,.25f,.24f,.24f,.25f,.25f,.26f,.26f,.25f,.25f),
            fatigueAvg = 4.2f, fatigueTrend = HealthTrend.WORSENING, painAvg = 0.5f,
            sleepAvg = 5.2f, sleepTrend = HealthTrend.WORSENING,
            sleepHistory = listOf(7.5f,7.3f,7.1f,6.9f,6.7f,6.5f,6.3f,6.1f,5.9f,5.7f,
                                  5.5f,5.3f,5.1f,4.9f,4.8f,4.8f,4.9f,5.0f,5.1f,5.2f,
                                  5.2f,5.3f,5.3f,5.2f,5.2f,5.1f,5.2f,5.2f,5.3f,5.3f),
            primaryMedication = "Metformin", primaryMedDose = "1000 mg",
            medications = listOf(
                GynacMedication("Metformin","1000 mg","Twice daily","05 Mar 2026",active=true),
            ),
            clinicalNotes = listOf(
                GynacNote("25 Aug 2026", NoteType.CONSULTATION, "Significant fatigue reported. Sleep deteriorating — 7.5h to under 5h over past month. Stress-related. Consider thyroid panel to rule out secondary cause.", DataSource.CLINICIAN),
            ),
            alerts = listOf(
                GynacAlert("Average sleep ↓ from 7.5h to 5.2h in 30 days", AlertSev.URGENT, 0),
                GynacAlert("Reported fatigue: 4.2/5 — highest in 6 months", AlertSev.WARNING, 3),
            ),
            dataCompleteness = 81, lastUpdateHours = 5,
        ),

        // 6. STABLE — long-term patient, well-controlled
        GynacPatient(
            id = "p006", name = "Kavitha Rao", age = 34, patientCode = "SA-000201",
            lastVisitDays = 35, nextFollowUpDays = 55,
            status = GynacStatus.STABLE,
            avgCycleLength = 32, recentCycleLength = 31, cycleTrend = HealthTrend.STABLE,
            cycleHistory = listOf(31, 32, 32, 33, 31, 31),
            acneSeverity = "None", acneTrend = HealthTrend.STABLE,
            acneHistory = List(30) { 0.08f + (it % 4) * 0.01f },
            fatigueAvg = 2.0f, fatigueTrend = HealthTrend.STABLE, painAvg = 0.1f,
            sleepAvg = 7.8f, sleepTrend = HealthTrend.STABLE,
            sleepHistory = List(30) { 7.6f + (it % 3) * 0.1f },
            primaryMedication = "Metformin", primaryMedDose = "500 mg",
            medications = listOf(
                GynacMedication("Metformin","500 mg","Once daily","Jan 2024",active=true,notes="Maintenance dose — 2 years"),
            ),
            clinicalNotes = listOf(
                GynacNote("09 Aug 2026", NoteType.FOLLOWUP, "Long-term management stable. Annual review satisfactory. Cycle regular 31–33 days. No complaints. Continue current plan.", DataSource.CLINICIAN),
            ),
            alerts = listOf(GynacAlert("Stable on long-term Metformin — annual review due in 2 months", AlertSev.INFO, 0)),
            dataCompleteness = 95, lastUpdateHours = 72,
        ),

        // 7. MONITORING — new patient, first month
        GynacPatient(
            id = "p007", name = "Sneha Iyer", age = 22, patientCode = "SA-000534",
            lastVisitDays = 8, nextFollowUpDays = 22,
            status = GynacStatus.MONITORING,
            avgCycleLength = 48, recentCycleLength = 48, cycleTrend = HealthTrend.VARIABLE,
            cycleHistory = listOf(55, 42, 51, 48, 60, 48),
            acneSeverity = "Moderate", acneTrend = HealthTrend.STABLE,
            acneHistory = List(30) { 0.45f + (it % 5) * 0.02f },
            fatigueAvg = 3.5f, fatigueTrend = HealthTrend.STABLE, painAvg = 0.6f,
            sleepAvg = 6.9f, sleepTrend = HealthTrend.STABLE,
            sleepHistory = List(30) { 6.7f + (it % 4) * 0.1f },
            primaryMedication = "Awaiting labs", primaryMedDose = "—",
            medications = listOf(
                GynacMedication("Folic Acid","5 mg","Daily","05 Sep 2026",active=true,notes="Baseline supplementation"),
            ),
            clinicalNotes = listOf(
                GynacNote("05 Sep 2026", NoteType.ASSESSMENT, "New patient. Highly irregular cycles since 18. Moderate acne. Ultrasound confirms polycystic ovaries. Awaiting AMH, fasting insulin, testosterone panel. Hold medication pending labs.", DataSource.CLINICIAN),
            ),
            alerts = listOf(
                GynacAlert("Lab results pending — booked 8 days ago", AlertSev.INFO, 0),
                GynacAlert("New patient — incomplete baseline data", AlertSev.INFO, 8),
            ),
            dataCompleteness = 52, lastUpdateHours = 12,
        ),

        // 8. FOLLOW_UP — OCP, due for review
        GynacPatient(
            id = "p008", name = "Pooja Nair", age = 29, patientCode = "SA-000445",
            lastVisitDays = 90, nextFollowUpDays = 3,
            status = GynacStatus.FOLLOW_UP,
            avgCycleLength = 28, recentCycleLength = 28, cycleTrend = HealthTrend.STABLE,
            cycleHistory = listOf(28, 28, 28, 28, 28, 28),
            acneSeverity = "Mild", acneTrend = HealthTrend.IMPROVING,
            acneHistory = List(30) { maxOf(0.05f, 0.40f - it * 0.012f) },
            fatigueAvg = 2.5f, fatigueTrend = HealthTrend.STABLE, painAvg = 0.2f,
            sleepAvg = 7.2f, sleepTrend = HealthTrend.STABLE,
            sleepHistory = List(30) { 7.0f + (it % 3) * 0.1f },
            primaryMedication = "OCP (Yasmin)", primaryMedDose = "Standard",
            medications = listOf(
                GynacMedication("Yasmin OCP","Standard","Once daily","15 Mar 2026",active=true,notes="3-month review due"),
            ),
            clinicalNotes = listOf(
                GynacNote("15 Mar 2026", NoteType.CONSULTATION, "Initiated OCP for cycle regulation and androgen suppression. Review at 6 months for BP check and side effect assessment.", DataSource.CLINICIAN),
            ),
            alerts = listOf(GynacAlert("6-month OCP review due in 3 days", AlertSev.WARNING, 0)),
            dataCompleteness = 78, lastUpdateHours = 26,
        ),

        // 9. STABLE — Inositol, well-controlled
        GynacPatient(
            id = "p009", name = "Aditi Gupta", age = 27, patientCode = "SA-000358",
            lastVisitDays = 42, nextFollowUpDays = 48,
            status = GynacStatus.STABLE,
            avgCycleLength = 33, recentCycleLength = 32, cycleTrend = HealthTrend.STABLE,
            cycleHistory = listOf(35, 34, 33, 33, 32, 32),
            acneSeverity = "Mild", acneTrend = HealthTrend.IMPROVING,
            acneHistory = List(30) { maxOf(0.10f, 0.35f - it * 0.008f) },
            fatigueAvg = 2.3f, fatigueTrend = HealthTrend.STABLE, painAvg = 0.2f,
            sleepAvg = 7.4f, sleepTrend = HealthTrend.STABLE,
            sleepHistory = List(30) { 7.2f + (it % 4) * 0.07f },
            primaryMedication = "Myo-Inositol", primaryMedDose = "4g",
            medications = listOf(
                GynacMedication("Myo-Inositol","4g","Daily","Feb 2026",active=true),
                GynacMedication("Vitamin D3","2000 IU","Daily","Feb 2026",active=true),
            ),
            clinicalNotes = listOf(
                GynacNote("02 Aug 2026", NoteType.FOLLOWUP, "Responding well to Inositol. Cycle shortening (35→32 days). Acne improving. No medication changes.", DataSource.CLINICIAN),
            ),
            alerts = listOf(GynacAlert("Stable — positive trend on Inositol therapy", AlertSev.INFO, 0)),
            dataCompleteness = 84, lastUpdateHours = 30,
        ),

        // 10. NEEDS REVIEW — new hair thinning symptom
        GynacPatient(
            id = "p010", name = "Riya Sharma", age = 31, patientCode = "SA-000481",
            lastVisitDays = 22, nextFollowUpDays = null,
            status = GynacStatus.NEEDS_REVIEW,
            avgCycleLength = 40, recentCycleLength = 42, cycleTrend = HealthTrend.WORSENING,
            cycleHistory = listOf(36, 38, 40, 41, 41, 42),
            acneSeverity = "Mild", acneTrend = HealthTrend.STABLE,
            acneHistory = List(30) { 0.28f + (it % 6) * 0.01f },
            fatigueAvg = 3.4f, fatigueTrend = HealthTrend.WORSENING, painAvg = 0.35f,
            sleepAvg = 6.4f, sleepTrend = HealthTrend.WORSENING,
            sleepHistory = List(30) { maxOf(5.5f, 7.0f - it * 0.05f) },
            primaryMedication = "Metformin", primaryMedDose = "500 mg",
            medications = listOf(
                GynacMedication("Metformin","500 mg","Once daily","Apr 2026",active=true),
            ),
            clinicalNotes = listOf(
                GynacNote("22 Aug 2026", NoteType.CONSULTATION, "Patient reporting new hair thinning over last 2 weeks. Also fatigue worsening. Check DHT, free testosterone, TSH. Consider adding Spironolactone if androgens elevated.", DataSource.CLINICIAN),
            ),
            alerts = listOf(
                GynacAlert("New symptom reported: hair thinning (2 weeks)", AlertSev.WARNING, 0),
                GynacAlert("Fatigue trend ↑ — review pending labs", AlertSev.WARNING, 2),
            ),
            dataCompleteness = 76, lastUpdateHours = 8,
        ),

        // 11. MONITORING — recently started tracking
        GynacPatient(
            id = "p011", name = "Nisha Reddy", age = 25, patientCode = "SA-000558",
            lastVisitDays = 14, nextFollowUpDays = 16,
            status = GynacStatus.MONITORING,
            avgCycleLength = 45, recentCycleLength = 47, cycleTrend = HealthTrend.VARIABLE,
            cycleHistory = listOf(40, 50, 42, 48, 44, 47),
            acneSeverity = "Severe", acneTrend = HealthTrend.WORSENING,
            acneHistory = List(30) { 0.65f + (it % 5) * 0.02f },
            fatigueAvg = 4.0f, fatigueTrend = HealthTrend.WORSENING, painAvg = 0.7f,
            sleepAvg = 5.8f, sleepTrend = HealthTrend.WORSENING,
            sleepHistory = List(30) { maxOf(4.5f, 6.5f - it * 0.06f) },
            primaryMedication = "Pending review", primaryMedDose = "—",
            medications = listOf(
                GynacMedication("Clindamycin gel","1%","Topical nightly","30 Aug 2026",active=true,notes="Dermatology referral given"),
            ),
            clinicalNotes = listOf(
                GynacNote("30 Aug 2026", NoteType.ASSESSMENT, "Severe acne, highly irregular cycles, significant fatigue. High clinical suspicion for PCOS. Referred to dermatology for acne. Endocrine panel ordered. Systemic treatment pending.", DataSource.CLINICIAN),
            ),
            alerts = listOf(
                GynacAlert("Severe acne — dermatology referral pending", AlertSev.URGENT, 0),
                GynacAlert("Endocrine panel results expected this week", AlertSev.WARNING, 1),
            ),
            dataCompleteness = 60, lastUpdateHours = 18,
        ),

        // 12. STABLE — diet + exercise
        GynacPatient(
            id = "p012", name = "Tanvi Mehta", age = 28, patientCode = "SA-000320",
            lastVisitDays = 55, nextFollowUpDays = 35,
            status = GynacStatus.STABLE,
            avgCycleLength = 31, recentCycleLength = 30, cycleTrend = HealthTrend.STABLE,
            cycleHistory = listOf(32, 31, 31, 30, 30, 30),
            acneSeverity = "None", acneTrend = HealthTrend.IMPROVING,
            acneHistory = List(30) { maxOf(0.05f, 0.25f - it * 0.007f) },
            fatigueAvg = 1.8f, fatigueTrend = HealthTrend.IMPROVING, painAvg = 0.1f,
            sleepAvg = 7.9f, sleepTrend = HealthTrend.IMPROVING,
            sleepHistory = List(30) { minOf(8.5f, 7.0f + it * 0.05f) },
            primaryMedication = "Diet + Exercise", primaryMedDose = "—",
            medications = listOf(
                GynacMedication("Vitamin D3","2000 IU","Daily","Jun 2026",active=true),
                GynacMedication("Omega-3","1g","Daily","Jun 2026",active=true),
            ),
            clinicalNotes = listOf(
                GynacNote("20 Jul 2026", NoteType.FOLLOWUP, "Excellent lifestyle adherence. BMI improved, cycle normalising. No pharmacological intervention needed at this time. 3-month review as planned.", DataSource.CLINICIAN),
            ),
            alerts = listOf(GynacAlert("Positive improvement — lifestyle-only management working well", AlertSev.INFO, 0)),
            dataCompleteness = 90, lastUpdateHours = 55,
        ),

        // 13. NEEDS REVIEW — teenage new case, first presentation
        GynacPatient(
            id = "p013", name = "Samaira Khan", age = 17, patientCode = "SA-000601",
            lastVisitDays = 3, nextFollowUpDays = 14,
            status = GynacStatus.NEEDS_REVIEW,
            avgCycleLength = 52, recentCycleLength = 58, cycleTrend = HealthTrend.WORSENING,
            cycleHistory = listOf(44, 46, 50, 52, 55, 58),
            acneSeverity = "Severe", acneTrend = HealthTrend.WORSENING,
            acneHistory = List(30) { i -> 0.55f + i * 0.012f },
            fatigueAvg = 4.2f, fatigueTrend = HealthTrend.WORSENING, painAvg = 0.5f,
            sleepAvg = 5.8f, sleepTrend = HealthTrend.WORSENING,
            sleepHistory = List(30) { 6.5f - it * 0.025f },
            primaryMedication = "Awaiting labs", primaryMedDose = "—",
            medications = listOf(
                GynacMedication("Folic Acid","5 mg","Once daily","10 Sep 2026",active=true,notes="Pre-investigative"),
            ),
            clinicalNotes = listOf(
                GynacNote("10 Sep 2026", NoteType.ASSESSMENT, "First presentation. 17F, menarche at 14, cycles always irregular. Severe facial acne. High androgen features suspected. Ordering fasting insulin, LH/FSH ratio, pelvic ultrasound. Consider Metformin post-labs.", DataSource.CLINICIAN),
                GynacNote("11 Sep 2026", NoteType.PATIENT_NOTE, "My periods have never been regular. Sometimes 2 months pass. My skin is so bad I can't go to college. Please help.", DataSource.PATIENT),
            ),
            alerts = listOf(
                GynacAlert("New patient — labs pending, no diagnosis confirmed yet", AlertSev.URGENT, 0),
                GynacAlert("Cycle 58 days — amenorrhoea risk, rule out pregnancy", AlertSev.URGENT, 0),
                GynacAlert("Severe cystic acne — strong androgen signal", AlertSev.WARNING, 0),
            ),
            dataCompleteness = 42, lastUpdateHours = 6,
        ),

        // 14. FOLLOW_UP — post-pregnancy PCOS reactivation
        GynacPatient(
            id = "p014", name = "Deepika Verma", age = 33, patientCode = "SA-000612",
            lastVisitDays = 21, nextFollowUpDays = 7,
            status = GynacStatus.FOLLOW_UP,
            avgCycleLength = 36, recentCycleLength = 38, cycleTrend = HealthTrend.VARIABLE,
            cycleHistory = listOf(28, 28, 42, 35, 40, 38),
            acneSeverity = "Mild", acneTrend = HealthTrend.STABLE,
            acneHistory = List(30) { 0.25f + (if (it % 7 < 3) 0.1f else 0f) },
            fatigueAvg = 3.9f, fatigueTrend = HealthTrend.STABLE, painAvg = 0.2f,
            sleepAvg = 5.2f, sleepTrend = HealthTrend.WORSENING,
            sleepHistory = List(30) { 5.0f + (if (it < 15) 0.1f * it else 1.5f - 0.05f * (it - 15)) },
            primaryMedication = "Metformin", primaryMedDose = "850 mg",
            medications = listOf(
                GynacMedication("Metformin","850 mg","Twice daily","22 Aug 2026",active=true,notes="Restarted post-partum"),
                GynacMedication("Vitamin D3","60,000 IU","Weekly","22 Aug 2026",active=true),
            ),
            clinicalNotes = listOf(
                GynacNote("22 Aug 2026", NoteType.CONSULTATION, "PCOS in remission during pregnancy, now 8 months post-partum. Cycles irregular again. Breastfeeding reduced — resuming Metformin 850 mg BD. Sleep disruption likely contributing. Review in 4 weeks.", DataSource.CLINICIAN),
            ),
            alerts = listOf(
                GynacAlert("Post-partum PCOS reactivation — insulin resistance reassessment needed", AlertSev.WARNING, 0),
                GynacAlert("Sleep avg 5.2h — cortisol impact on cycle likely", AlertSev.INFO, 0),
            ),
            dataCompleteness = 68, lastUpdateHours = 18,
        ),

        // 15. MONITORING — thyroid-PCOS overlap, well controlled
        GynacPatient(
            id = "p015", name = "Sunita Pillai", age = 38, patientCode = "SA-000558",
            lastVisitDays = 45, nextFollowUpDays = 45,
            status = GynacStatus.MONITORING,
            avgCycleLength = 31, recentCycleLength = 30, cycleTrend = HealthTrend.STABLE,
            cycleHistory = listOf(33, 31, 32, 30, 31, 30),
            acneSeverity = "Mild", acneTrend = HealthTrend.STABLE,
            acneHistory = List(30) { 0.20f + (if (it % 10 == 5) 0.08f else 0f) },
            fatigueAvg = 2.8f, fatigueTrend = HealthTrend.STABLE, painAvg = 0.1f,
            sleepAvg = 6.9f, sleepTrend = HealthTrend.STABLE,
            sleepHistory = List(30) { 6.8f + (if (it % 14 < 2) -0.5f else 0f) },
            primaryMedication = "Levothyroxine", primaryMedDose = "50 mcg",
            medications = listOf(
                GynacMedication("Levothyroxine","50 mcg","Morning, empty stomach","Jan 2024",active=true),
                GynacMedication("Metformin","500 mg","Once daily","Mar 2025",active=true,notes="Thyroid-PCOS overlap"),
            ),
            clinicalNotes = listOf(
                GynacNote("30 Jul 2026", NoteType.FOLLOWUP, "TSH 2.1 — within range on Levothyroxine 50 mcg. Cycle regular. Weight stable. PCOS symptoms minimal. Continue current regimen, 3-monthly thyroid function test.", DataSource.CLINICIAN),
            ),
            alerts = listOf(
                GynacAlert("Thyroid function test due in 6 weeks", AlertSev.INFO, 0),
            ),
            dataCompleteness = 82, lastUpdateHours = 102,
        ),

        // 16. STABLE — long-term management, post-menopausal transition monitoring
        GynacPatient(
            id = "p016", name = "Rekha Nambiar", age = 43, patientCode = "SA-000089",
            lastVisitDays = 60, nextFollowUpDays = 30,
            status = GynacStatus.STABLE,
            avgCycleLength = 35, recentCycleLength = 38, cycleTrend = HealthTrend.VARIABLE,
            cycleHistory = listOf(32, 34, 38, 42, 36, 38),
            acneSeverity = "Mild", acneTrend = HealthTrend.STABLE,
            acneHistory = List(30) { 0.18f },
            fatigueAvg = 2.2f, fatigueTrend = HealthTrend.STABLE, painAvg = 0.1f,
            sleepAvg = 7.1f, sleepTrend = HealthTrend.STABLE,
            sleepHistory = List(30) { 7.0f + (if (it % 10 < 2) -0.4f else 0f) },
            primaryMedication = "Metformin", primaryMedDose = "500 mg",
            medications = listOf(
                GynacMedication("Metformin","500 mg","Once daily","May 2022",active=true,notes="Long-term, review annually"),
                GynacMedication("OCP (prev)","—","—","2019","Apr 2022",active=false,notes="Discontinued — planning family"),
            ),
            clinicalNotes = listOf(
                GynacNote("15 Jul 2026", NoteType.FOLLOWUP, "Stable on Metformin 500 mg since 2022. Perimenopause markers appearing — occasional hot flush, cycles lengthening slightly. AMH low but expected for age. Reassess HRT candidacy at next visit.", DataSource.CLINICIAN),
            ),
            alerts = listOf(
                GynacAlert("Cycles lengthening — possible perimenopause onset, monitor AMH", AlertSev.INFO, 0),
            ),
            dataCompleteness = 76, lastUpdateHours = 144,
        ),
    )

    val needsAttention get() = patients.filter {
        it.status == GynacStatus.NEEDS_REVIEW || it.status == GynacStatus.FOLLOW_UP
    }.sortedWith(compareBy<GynacPatient> { it.status != GynacStatus.NEEDS_REVIEW }
        .thenBy { it.lastUpdateHours })
}

data class GynacDoctor(
    val name: String,
    val credentials: String,
    val specialty: String,
    val clinic: String,
    val activePatients: Int,
    val followUpsToday: Int,
    val newUpdates: Int,
)
