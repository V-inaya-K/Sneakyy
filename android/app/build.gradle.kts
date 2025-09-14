plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.example.privacy_guard_flutter"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.privacy_guard_flutter"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    // ML Kit Face Detection
    implementation("com.google.mlkit:face-detection:16.1.6")

    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
