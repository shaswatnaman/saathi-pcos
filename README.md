<div align="center">

<img src="logo.png" width="120" alt="Saathi Logo" />

# Saathi

### The PCOS Companion Built for India

*Track. Understand. Share — entirely on-device.*

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Offline First](https://img.shields.io/badge/Offline-First-blue)]()
[![No Cloud](https://img.shields.io/badge/Cloud-None-critical)]()
[![iQOO Hackathon](https://img.shields.io/badge/iQOO%20Hackathon%202026-HealthTech%20Track-orange)]()

**Team leadmilers · Shaswat Naman & Priyani**

</div>

---

## The Problem

**1 in 5 Indian women has PCOS. Most wait over 2 years for a diagnosis.**

That gap isn't a shortage of doctors — it's a shortage of *data*. A woman arrives at a 10-minute gynaecology appointment with months of lived experience she can only describe verbally. The doctor gets a sketch. Subtle patterns — a cycle creeping from 28 days to 38 over six months, fatigue that's getting worse, skin that's been darkening since last Diwali — stay invisible because no one was recording them.

Existing health apps make this worse, not better:

- Built for Western bodies with Western clinical thresholds
- Designed for one person, ignoring India's family-centred healthcare reality
- Require internet, accounts, and English fluency
- Give data to the patient but nothing to the doctor

**Saathi fixes all of this.**

---

## What Saathi Does

Saathi is an **offline-first Android health companion** for Indian women managing PCOS, structured around three roles that reflect how healthcare actually works in Indian households:

| Role | Who | What they get |
|------|-----|---------------|
| 🌸 **Self** | The patient | Daily logging, AI-powered symptom tracking, full insights, PDF export |
| 🤝 **Companion** | A trusted family member | Live sync of cycle & symptom summary over Wi-Fi |
| 🩺 **Gynac** | The doctor | Full patient history dashboard, clinical PDF, structured logs |

---

## Features

### 🌸 Self — For the Patient

**Daily Logging Without Friction**
- Voice check-ins capture energy and mood — no typing required
- Camera-based acne scoring using on-device AI — just take a selfie
- Symptom log: fatigue (1–5), weight, waist-hip ratio, skin darkening, hair changes, flow intensity
- Cycle tracking with phase detection (Menstrual / Follicular / Ovulation / Luteal)

**Insights That Actually Mean Something**
- 4-tab Insights screen: **Charts · Cycles · Symptoms · Risk**
- Charts show energy trajectory, skin activity against cycle phase, acne calibration by cycle day, risk trend
- Raw log tabs show every entry — cycle lengths, full symptom breakdowns, risk sub-scores
- Time-range filtering: 7d / 30d / 90d

**Export & Share**
- One-tap **Export PDF** generates a 3-page health report saved to device
- Report includes summary, 4-week trend chart, and symptom log table
- Share directly with any doctor, even one not using Saathi

---

### 🤝 Companion — For the Family

**The only PCOS app with a family support role.**

- Install in Companion mode on a second device (phone or tablet)
- Receives a live sync of the patient's cycle phase, fatigue level, and flagged symptoms
- Works over the same Wi-Fi network — **no internet required**
- Sends short supportive notes back to the patient
- Turns the natural Indian joint-family caregiving instinct into structured health support

---

### 🩺 Gynac — For the Doctor

**Closes the clinic data gap.**

- Dedicated Gynac mode with a patient dashboard showing status at a glance
- **16 realistic demo patients** covering the full clinical spectrum: teenage first presentation, post-pregnancy PCOS reactivation, thyroid-PCOS overlap, perimenopause, long-term management
- When a patient connects over LAN, receives **30 entries of full history** — cycle logs, symptom entries with acne scores and PCOS flags, risk assessments with sub-scores
- **5-tab patient profile**: Overview · Timeline · Logs · Medications · Notes
- **Logs tab** has sub-tabs for Cycles, Symptoms, and Risk — every entry, dated, with flag chips
- **Clinical PDF** (3 pages): patient identity block, timeline charts, medication table, annotation space — ready to print or file

---

## India-Specific Design

**This isn't a global app localised for India. It was designed for India from scratch.**

| Design Choice | Why It Matters for India |
|--------------|--------------------------|
| **WHtR thresholds calibrated for Indian bodies** | <0.46 healthy · 0.46–0.49 borderline · 0.49–0.54 increased · >0.54 high — Indian women develop metabolic complications at lower BMI than Western populations |
| **Acanthosis nigricans as a primary signal** | Skin darkening at neck/underarms is a prevalent early PCOS marker in Indian skin tones — tracked as a first-class symptom, not an afterthought |
| **Companion role** | Indian healthcare decisions are family decisions — Saathi makes a family member an informed participant, not a worried bystander |
| **No internet dependency** | Works in semi-urban homes, clinic waiting rooms, and on the road — where connectivity is intermittent |
| **No account, no login** | Removes the friction barrier for first-generation smartphone users |
| **Privacy by architecture** | Photos never leave the device — critical for intimate health data in conservative households |
| **Risk model trained on Indian PCOS data** | Kaggle PCOS dataset (541 Indian patients, CV-AUC 0.862) — not extrapolated from Western cohorts |

---

## Technical Architecture

### On-Device AI Stack

```
Camera Frame
    └─► AcneClassifier (ONNX + HSV colour-space)
            ├── Redness ratio (inflammatory acne)
            ├── PIH dark-spot density (post-inflammatory hyperpigmentation)
            └── Texture variance
                    └─► acneScore: Float (0.0 – 1.0) — stored, never the photo

Voice Recording (10s)
    └─► VoiceAnalyzer (MediaRecorder RMS energy)
            └─► voiceEnergyScore: Float — fatigue proxy

RiskScorer (logistic regression, 10-fold CV-AUC 0.862)
    ├── cycleScore (irregularity, length deviation)
    ├── acneScore (camera + manual flags)
    ├── fatigueScore (voice energy + logged fatigue)
    └── physicalScore (WHtR, weight trend, PCOS flags)
            └─► totalScore: Int (0 – 100) + riskLevel: String
```

### LAN Sync (No Internet Required)

```
Patient Device (SELF)
    └─► TCP ServerSocket (random port)
    └─► UDP Broadcast every 2s
            "saathi-TOKEN:PORT" → 255.255.255.255 + subnet broadcast
                                        ↓
Gynac/Companion Device
    └─► UDP listener on port 7423
    └─► Matches any "saathi-" beacon
    └─► Connects TCP → receives SyncPacket JSON
            ├── Latest snapshot (cycle day, phase, fatigue, flags)
            └── Full history arrays (30× cycles, symptoms, risk)
```

- **No mDNS / NSD** — replaced with UDP subnet broadcast, works on standard home and clinic routers where multicast is blocked
- **No pairing prerequisite** — devices on the same Wi-Fi find each other automatically
- **`WifiManager.MulticastLock`** acquired on both sides during sync window
- **`WifiManager.DhcpInfo`** used to compute actual subnet broadcast address (e.g. `192.168.1.255`)

### Data Layer

```
Room Database (SQLite, no cloud)
    ├── CycleLog        (date, cycleLength, flowIntensity, notes)
    ├── SymptomLog      (date, fatigue, acneScore, voiceEnergy, weight,
    │                    waistHip, skinDarkening, weightGain, hairIssues)
    ├── RiskAssessment  (date, totalScore, cycleScore, acneScore,
    │                    fatigueScore, physicalScore, riskLevel)
    └── HealthEntry     (moduleId, entryType, value, timestamp)
```

### PDF Generation

- **Patient PDF** (`ReportGenerator`): 3 pages — summary tile, 4-week trend, symptom log table
- **Clinical PDF** (`GynacReportGenerator`): 3 pages — patient identity block, timeline charts, medication table
- Both use Android native `PdfDocument` + `Canvas` drawing
- Saved to app-private external storage, shared via `FileProvider`

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose (Material 3) |
| Navigation | Navigation Compose |
| Database | Room 2.7 |
| Camera | CameraX |
| AI inference | ONNX Runtime (on-device, no server) |
| Charts | MPAndroidChart (SELF PDF) + native Canvas (Gynac PDF) |
| LAN transport | `DatagramSocket` (UDP) + `ServerSocket` / `Socket` (TCP) |
| PDF | Android `PdfDocument` + `FileProvider` |
| Minimum API | 26 (Android 8.0) |
| Target API | 37 (Android 15) |

---

## Our Moats

**1. Three-role architecture — no one else has this.**
Self + Companion + Gynac in a single app creates a network where the patient's data flows to the people who need it most, without leaving the local network. Adoption by one person pulls in two more.

**2. Truly offline, truly private.**
Zero cloud, zero accounts, zero permissions for photos. The LAN sync works in a clinic with no internet. Raw images never leave the device — ever. This is the only architecture Indian women in semi-urban and conservative households will actually trust with intimate health data.

**3. India-calibrated clinical intelligence.**
WHtR thresholds for Indian body types, acanthosis nigricans as a first-class PCOS signal, and a risk model trained on Indian patient data. Not a Western app with a Hindi splash screen.

**4. Clinic-ready tooling for the doctor.**
Most health apps ignore the doctor entirely. Saathi gives a gynaecologist a full 30-entry history the moment a patient connects — dated, structured, with clinical sub-scores — and a formatted PDF they can file. It makes a 10-minute appointment 3x more useful.

**5. Frictionless data collection.**
Voice check-in (no typing), camera acne scoring (one selfie), on-device AI (no upload wait). The daily habit that most health apps fail to build is easy here because input friction is near zero.

**6. Platform defensibility.**
The three-role network effect, the on-device AI pipeline, and the India-specific clinical calibration compound over time. Each additional data point makes the risk model more accurate. Each additional role adoption makes the app stickier.

---

## Build & Run

### Requirements

| Tool | Version |
|------|---------|
| Android Studio | Ladybug (2024.2)+ |
| JDK | 17 |
| Android SDK | API 37 |
| Minimum device | API 26 (Android 8.0) |

### Clone & Build

```bash
git clone https://github.com/shaswatnaman/saathi-pcos.git
cd saathi-pcos
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### First Launch

The app auto-seeds demo data on first install — 16 gynac patients and 30 days of PCOS health history are ready immediately. No setup required.

### LAN Sync Demo (two devices)

1. Open Saathi on Device A → complete onboarding → select **Self**
2. Open Saathi on Device B → select **Gynac**
3. Both devices on the same Wi-Fi
4. Gynac screen auto-discovers and connects — full history appears in the **Logs** tab

### Permissions

| Permission | Purpose |
|-----------|---------|
| `CAMERA` | Front-camera acne analysis |
| `RECORD_AUDIO` | Voice energy fatigue scoring |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_MULTICAST_STATE` | LAN device discovery |
| `ACCESS_FINE_LOCATION` | Required by Android for Wi-Fi scanning (not used for location) |

---

## Repository Structure

```
app/src/main/java/com/leadmilers/saathi/
├── SaathiApp.kt                    Application class
├── MainActivity.kt                 Navigation host
├── companion/
│   ├── LanSyncManager.kt           UDP broadcast + TCP sync engine
│   ├── SyncManager.kt              Builds and pushes full history packet
│   └── SyncPacket.kt               JSON-serialisable data transfer object
├── data/
│   ├── entity/                     Room entities
│   ├── dao/                        Flow-based DAOs
│   └── repository/SaathiRepository.kt
├── gynac/
│   ├── GynacPatient.kt             Clinical patient model
│   ├── GynacDemoData.kt            16 realistic demo patients
│   └── ui/
│       ├── GynacHomeScreen.kt      Patient dashboard + live sync banner
│       └── GynacPatientProfileScreen.kt  5-tab profile (Logs tab with full history)
├── ml/
│   ├── AcneClassifier.kt           HSV colour-space acne analyser
│   ├── VoiceAnalyzer.kt            RMS energy voice fatigue proxy
│   └── RiskScorer.kt               Logistic regression (CV-AUC 0.862)
├── report/
│   ├── ReportGenerator.kt          3-page patient PDF
│   └── GynacReportGenerator.kt     3-page clinical PDF
└── ui/screen/
    ├── HomeScreen.kt               Risk card, insights, quick actions
    ├── InsightsScreen.kt           Charts + Cycles + Symptoms + Risk tabs
    ├── CameraScreen.kt             CameraX acne analysis
    ├── LogScreen.kt                Daily symptom entry
    └── ReportScreen.kt             PDF generation UI
```

---

## License

Built for the iQOO Hackathon 2026 · HealthTech Track.  
Not licensed for distribution or commercial use.

---

<div align="center">

Made with ❤️ for 100 million Indian women living with PCOS

**Team leadmilers** · Shaswat Naman · Priyani

</div>
