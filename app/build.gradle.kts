plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.blaze.circuitlab"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.blaze.circuitlab"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.2.9-native-powered-amplifier"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// JVM-only logic checks in CI. Device and KiCad acceptance remain separate.
dependencies { testImplementation("junit:junit:4.13.2") }
