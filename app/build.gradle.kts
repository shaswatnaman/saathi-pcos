import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
}

android {
    namespace = "com.leadmilers.saathi"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.leadmilers.saathi"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProps = Properties()
        localProps.load(rootProject.file("local.properties").inputStream())
        buildConfigField("String", "OPENROUTER_API_KEY",
            "\"${localProps["openrouter.api.key"] ?: ""}\"")

    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Room 2.7.0 — compatible with Kotlin 2.2.x
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // AcneClassifier uses pure Android Bitmap analysis (no TFLite dependency needed)

    // MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Accompanist permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // HTTP client for cloud face analysis
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Icons extended
    implementation("androidx.compose.material:material-icons-extended")

    // Google Fonts (Inter)
    implementation("androidx.compose.ui:ui-text-google-fonts")

    // Health Connect — step count, sleep (reads from Origin Health / Google Fit / wearable)
    implementation("androidx.health.connect:connect-client:1.1.0-rc01")

    // Splash screen API (backport to API 26+)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Nearby Connections — local device-to-device encrypted sync (no cloud)
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    // ZXing core — QR code generation (no UI, just bitmap encoding)
    implementation("com.google.zxing:core:3.5.3")

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
