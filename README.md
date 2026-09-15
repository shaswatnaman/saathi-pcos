# Saathi — PCOS Cross-Signal Tracker.

**iQOO Hackathon 2026 · HealthTech Track · Team leadmilers**

Saathi is an on-device Android app that tracks PCOS risk across three simultaneous signals — acne (camera), voice fatigue (microphone), and cycle irregularity — and combines them using a logistic regression model trained on 541 real patients (CV-AUC 0.862).

---

## Requirements

| Tool | Version |
|------|---------|
| Android Studio | Ladybug (2024.2) or newer |
| JDK | 17 (bundled with Android Studio) |
| Android SDK | API 37 (targetSdk) |
| Minimum device | API 26 (Android 8.0) |
| Gradle | 9.6.0 (downloaded automatically) |

> **No API keys, no Firebase, no backend.** Everything runs 100% on-device.

---

## Clone & Open

```bash
git clone https://github.com/shaswatnaman/saathi-pcos.git
cd saathi-pcos
```

Open the `saathi-pcos` folder in Android Studio (**File → Open**). Let Gradle sync finish (first sync downloads ~200 MB of dependencies — needs internet).

---

## Run on a Physical Device (Recommended)

1. On your Android phone: **Settings → About Phone → tap "Build Number" 7 times** → Developer Options unlocked
2. **Settings → Developer Options → USB Debugging → ON**
3. Connect phone via USB cable
4. In Android Studio: select your device in the toolbar → click **Run ▶**

The app installs and opens automatically.

---

## Run on an Emulator

1. In Android Studio: **Device Manager → Create Virtual Device**
2. Choose **Pixel 7** (or any Phone) → select **API 34** system image → Finish
3. Start the emulator, then click **Run ▶**

> Camera and Voice tabs require a physical device (emulator mic/camera are limited). Use **Settings → Load Demo Data** instead to populate 30 days of realistic data without needing camera or microphone.

---

## First Launch — Load Demo Data

The app starts with an empty database. To see the full PCOS risk story:

1. Open the app → tap the **⚙ gear icon** (top-right on Home screen)
2. Tap **Load Demo Data**
3. Wait for the snackbar: *"30 days of demo data loaded!"*
4. Go back to **Home** → pull down to refresh

You should see a **purple CRITICAL — SEE DOCTOR** card, Score 9/12, and the 30-day trend chart.

---

## App Structure

```
app/src/main/java/com/leadmilers/saathi/
├── MainActivity.kt          ← Navigation host (6-tab bottom nav)
├── SaathiApp.kt             ← Application class, singleton repository
├── data/
│   ├── entity/              ← Room entities: CycleLog, SymptomLog, RiskAssessment
│   ├── dao/                 ← DAOs with Flow-based queries
│   ├── db/SaathiDatabase.kt ← Room database v2
│   └── repository/          ← SaathiRepository singleton
├── ml/
│   ├── AcneClassifier.kt    ← HSV colour-space acne analyser (no TFLite dep)
│   ├── VoiceAnalyzer.kt     ← MediaRecorder RMS energy analyser
│   └── RiskScorer.kt        ← Logistic regression (CV-AUC 0.862) + 0-12 score
├── report/ReportGenerator.kt← 3-page PdfDocument (A4), saved to Downloads
├── office/OfficeBridge.kt   ← iQOO Office Kit SDK (reflection, graceful fallback)
├── demo/DemoDataSeeder.kt   ← Seeds 30 days of escalating PCOS data
└── ui/
    ├── screen/
    │   ├── HomeScreen.kt    ← Risk card, metric tiles, sparkline chart
    │   ├── LogScreen.kt     ← Manual daily entry form
    │   ├── CameraScreen.kt  ← CameraX front-camera acne analysis
    │   ├── VoiceScreen.kt   ← 10-second voice recording + energy score
    │   ├── ReportScreen.kt  ← PDF generation UI
    │   ├── OfficeScreen.kt  ← Mirror / clipboard / transfer to laptop
    │   └── SettingsScreen.kt← Demo data loader, clear all, app info
    └── viewmodel/HomeViewModel.kt
```

---

## Key Technical Decisions

### Why no TFLite?
TFLite 2.14.0 declares duplicate Android namespaces across `tensorflow-lite`, `tensorflow-lite-api`, and `tensorflow-lite-gpu`. AGP 9.4.0 strictly enforces namespace uniqueness (no suppressible flag exists). `AcneClassifier` uses a pure-Android HSV colour-space analyser instead — redness ratio, PIH dark-spot density, and texture variance — calibrated to match clinical acne severity scores.

### Risk Model
`RiskScorer.kt` runs logistic regression trained on the [Kaggle PCOS Dataset](https://www.kaggle.com/datasets/prasoonkottarathil/polycystic-ovary-syndrome-pcos) (541 patients, 10-fold CV-AUC 0.862). Waist-hip ratio was excluded — 97% of Indian non-PCOS subjects exceed the 0.80 clinical threshold, making it non-discriminating for this population.

### Room Database
Version 2 with `fallbackToDestructiveMigration(true)`. Entities: `CycleLog`, `SymptomLog` (with PCOS flags: skinDarkening, weightGain, hairIssues), `RiskAssessment`.

### iQOO Office Kit
`OfficeBridge` uses reflection to call `com.vivo.officekit.OfficeKitManager` so the app compiles and runs on any Android device. `isAvailable()` returns false on non-iQOO hardware; all three operations (mirror, clipboard, transfer) return false gracefully.

---

## Build from Command Line

```bash
# Debug APK
./gradlew assembleDebug

# Install directly to connected device
./gradlew installDebug

# Or install the built APK manually
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Permissions Used

| Permission | Used for |
|-----------|---------|
| `CAMERA` | Front-camera acne analysis (CameraScreen) |
| `RECORD_AUDIO` | Voice energy measurement (VoiceScreen) |
| `WRITE_EXTERNAL_STORAGE` | PDF save on Android ≤ 8 (API 28 max) |

All permissions are requested at runtime via Accompanist Permissions.

---

## Team

**leadmilers** — iQOO Hackathon 2026, HealthTech Track  
Shaswat Naman · Priyani

---

## License

For hackathon evaluation only. Not for distribution.
