plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.indrive.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.indrive.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("permanent") {
            storeFile = file("keystore/fixed-release.keystore")
            storePassword = "thedriver123"
            keyAlias = "thedriver"
            keyPassword = "thedriver123"
        }
    }

    buildTypes {
        debug {
            if (file("keystore/fixed-release.keystore").exists()) {
                signingConfig = signingConfigs.getByName("permanent")
            }
        }
        release {
            if (file("keystore/fixed-release.keystore").exists()) {
                signingConfig = signingConfigs.getByName("permanent")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")

    // Room Database for background telemetry & overlay persistence
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
