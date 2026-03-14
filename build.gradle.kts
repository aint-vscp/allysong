// ============================================================================
// AllySong – Root Build Script
// ============================================================================
// Version catalog for the entire project. Individual module build files
// reference these constants to keep dependency versions synchronized.
// ============================================================================

plugins {
    // Applied with `apply false` so subprojects opt-in individually.
    id("com.android.application")       version "8.2.2"  apply false
    id("com.android.library")           version "8.2.2"  apply false
    id("org.jetbrains.kotlin.android")  version "1.9.22" apply false
    id("org.jetbrains.kotlin.multiplatform") version "1.9.22" apply false
    id("org.jetbrains.compose")         version "1.5.12" apply false
}
