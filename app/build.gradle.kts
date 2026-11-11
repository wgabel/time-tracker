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
        versionCode = 2
        versionName = "0.2"
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
