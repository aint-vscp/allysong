// ============================================================================
// AllySong – Settings
// Root settings file declares plugin repositories and project modules.
// ============================================================================

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AllySong"

// ── Module declarations ─────────────────────────────────────────────────────
// :shared   → KMP module (commonMain + androidMain); contains all shared
//             business logic, domain interfaces, Compose UI, and platform
//             actual implementations.
// :androidApp → Thin Android shell; wires the shared module into an Activity.
// ─────────────────────────────────────────────────────────────────────────────
include(":shared")
include(":androidApp")
