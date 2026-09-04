plugins {
    id("com.android.application") version "8.2.0"
    id("org.jetbrains.kotlin.android") version "1.9.20"
}
android {
    namespace = "com.example.prognosismeteo"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.prognosismeteo"
        minSdk = 24
        targetSdk = 34
    }
}
