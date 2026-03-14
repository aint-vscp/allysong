// ============================================================================
// AllySong – :shared module
// ============================================================================
// This is the KMP module that holds ALL shared business logic, domain
// interfaces, Compose Multiplatform UI, and platform `actual` implementations.
//
// Source sets:
//   commonMain  → Platform-agnostic code (interfaces, Compose UI, ViewModels)
//   androidMain → Android-specific implementations (Sensors, BLE, TFLite)
// ============================================================================

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
}

kotlin {
    // ── Android target ──────────────────────────────────────────────────────
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    // ── Source set dependency tree ───────────────────────────────────────────
    sourceSets {

        // ── commonMain ──────────────────────────────────────────────────────
        // Platform-agnostic: domain models, interfaces, Compose UI, ViewModels
        val commonMain by getting {
            dependencies {
                // Compose Multiplatform (shared UI toolkit)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.animation)
                implementation(compose.materialIconsExtended)

                // Kotlin Coroutines (StateFlow, structured concurrency)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

                // Kotlinx DateTime (cross-platform timestamps)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

                // Note: kotlinx-serialization is NOT used in this prototype.
                // The mesh controller uses manual JSON serialization to avoid
                // requiring the serialization compiler plugin in commonMain.
                // Add kotlinx-serialization-json here if @Serializable is needed.
            }
        }

        // ── androidMain ─────────────────────────────────────────────────────
        // Android platform implementations: real sensors, BLE, TFLite
        val androidMain by getting {
            dependencies {
                // Android Compose integration
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

                // Coroutines Android dispatcher
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

                // ── TensorFlow Lite (Edge AI) ───────────────────────────────
                // Quantized TinyML model inference for seismic-wave detection
                implementation("org.tensorflow:tensorflow-lite:2.14.0")
                implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

                // ── Google Nearby Connections (BLE mesh) ────────────────────
                // Decentralized peer-to-peer communication over BLE/Wi-Fi Direct
                implementation("com.google.android.gms:play-services-nearby:19.1.0")

                // ── Google Play Services Location (GPS) ──────────────────────
                // FusedLocationProviderClient for battery-efficient GPS tracking
                implementation("com.google.android.gms:play-services-location:21.1.0")

                // ── Android Sensors ─────────────────────────────────────────
                // No extra dependency needed – android.hardware.SensorManager
                // is part of the Android SDK.
            }
        }
    }
}

// ── Android library configuration ───────────────────────────────────────────
android {
    namespace = "com.allysong.shared"
    compileSdk = 34

    defaultConfig {
        minSdk = 26 // BLE mesh + Nearby Connections require API 26+
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += "tflite"
    }
}
