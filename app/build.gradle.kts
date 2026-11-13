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
        // GitHub Actions run numbers increment for each new workflow run.
        // Keep local builds usable without GitHub environment variables.
        val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.takeIf { it > 0 }
        versionCode = 1000 + (ciRunNumber ?: 0)

        // The adjective and animal are selected deterministically per run:
        // rebuilding the same run never changes the version identity.
        val colors = listOf("amber", "azure", "blue", "coral", "crimson", "golden",
            "green", "indigo", "ivory", "jade", "lavender", "olive", "orange",
            "pink", "purple", "red", "silver", "teal", "turquoise", "violet")
        val animals = listOf("badger", "bear", "bison", "cat", "deer", "dolphin",
            "eagle", "falcon", "fox", "gecko", "heron", "jaguar", "koala",
            "lynx", "otter", "owl", "panda", "rabbit", "tiger", "wolf")
        val seed = ciRunNumber ?: 0
        val color = colors[java.util.Random(seed.toLong()).nextInt(colors.size)]
        val animal = animals[java.util.Random((seed xor 0x5A17).toLong()).nextInt(animals.size)]
        versionName = "0.1.${ciRunNumber ?: 0}-$color-$animal"
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
