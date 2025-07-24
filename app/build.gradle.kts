plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "cics.csup.qrattendancecontrol"
    compileSdk = 36
    //

    defaultConfig {
        applicationId = "cics.csup.qrattendancecontrol"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildToolsVersion = "36.0.0"
    ndkVersion = "29.0.13599879 rc2"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // QR Scanner
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Firebase Core
    implementation("com.google.firebase:firebase-analytics:23.0.0")

    // Firestore
    implementation("com.google.firebase:firebase-firestore:26.0.0")

    // Firebase Auth
    implementation("com.google.firebase:firebase-auth:24.0.0")

    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))

    // AndroidX + UI
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
