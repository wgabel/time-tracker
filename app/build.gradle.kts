plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "pl.wgabel.timetracker"
    compileSdk = 35
    defaultConfig {
        applicationId = "pl.wgabel.timetracker"
        minSdk = 26
        targetSdk = 35
        // Every CI run must have a higher versionCode than the previous release.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 1000 + it } ?: 1000
        versionName = "0.3"
    }
    val signingFile = System.getenv("ANDROID_KEYSTORE_FILE")
    val signingPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    if (!signingFile.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        signingConfigs {
            create("releaseKey") {
                storeFile = file(signingFile)
                storePassword = signingPassword
                keyAlias = "time-tracker"
                keyPassword = signingPassword
            }
        }
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("releaseKey")
                isMinifyEnabled = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
