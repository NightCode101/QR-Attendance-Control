plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "cics.csup.qrattendancecontrol"
    compileSdk = 34 //

    defaultConfig {
        applicationId = "cics.csup.qrattendancecontrol"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "2.1"

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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // QR Scanner
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Firebase Core
    implementation("com.google.firebase:firebase-analytics:21.6.1")

    // Firestore
    implementation("com.google.firebase:firebase-firestore:24.4.5")

    // Firebase Auth
    implementation("com.google.firebase:firebase-auth:22.3.1")

    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))

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
