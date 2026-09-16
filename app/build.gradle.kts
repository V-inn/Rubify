plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rubify"
    compileSdk = 36

    defaultConfig {
        // Permanent once the first build is uploaded to Play.
        applicationId = "com.rubify"
        // AccessibilityService.takeScreenshot() is API 30. This is the floor.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            // Enabled once ML Kit is in and its keep rules are verified.
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        // BuildConfig.DEBUG gates the debug-only screenshot dumps.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Must match compileOptions above, or AGP fails on inconsistent JVM targets.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
