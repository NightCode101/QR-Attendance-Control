plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

// Renamed 'camerax_version' to 'cameraxVersion'
val cameraxVersion = "1.3.1"

android {
    signingConfigs {
        create("release") {
            storeFile = file("C:\\Sign Key\\releasekey.jks")
            storePassword = "Baoit@0601"
            keyAlias = "NightCode101"
            keyPassword = "Baoit@0601"
        }
    }
    namespace = "cics.csup.qrattendancecontrol"
    compileSdk = 34

    defaultConfig {
        applicationId = "cics.csup.qrattendancecontrol"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        signingConfig = signingConfigs.getByName("release")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Google ML Kit for Barcode Scanning (via Google Play Services)
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.0")

    // --- THIS IS THE FIX ---
    // CameraX (Google's modern camera library)
    // Now using $cameraxVersion
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    // --- END OF FIX ---

    // Local Broadcast Manager
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // Firebase BoM (manages versions automatically)
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))

    // Firebase (no versions when using BoM)
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")

    // AndroidX + UI (using your libs catalog)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Swipe to Refresh
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}