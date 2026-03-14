// ============================================================================
// AllySong – :androidApp module
// ============================================================================
// Thin Android shell application. This module:
//   1. Declares the AndroidManifest with required BLE/sensor permissions.
//   2. Hosts the single-Activity entry point (MainActivity).
//   3. Wires the :shared KMP module into the Android runtime.
// ============================================================================

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.allysong"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.allysong"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-prototype"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../allysong-release.jks")
            storePassword = "AllySong2026@ASEANYouthAI2026"
            keyAlias = "allysong"
            keyPassword = "AllySong2026@ASEANYouthAI2026"
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        noCompress += "tflite"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // The shared KMP module provides everything: UI, ViewModels, domain logic
    implementation(project(":shared"))

    // Android Compose BOM for consistent versioning
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Nearby Connections (needed at app level for service binding)
    implementation("com.google.android.gms:play-services-nearby:19.1.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
