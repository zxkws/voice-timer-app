plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 1
val ciVersionName = providers.gradleProperty("versionName").orNull ?: "0.1.0"

android {
    namespace = "com.zxkws.voicetimer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zxkws.voicetimer"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("VOICE_TIMER_KEYSTORE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("VOICE_TIMER_STORE_PASSWORD")
                keyAlias = System.getenv("VOICE_TIMER_KEY_ALIAS")
                keyPassword = System.getenv("VOICE_TIMER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val hasReleaseKey = !System.getenv("VOICE_TIMER_KEYSTORE").isNullOrBlank()
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else null
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    testImplementation("junit:junit:4.13.2")
}
