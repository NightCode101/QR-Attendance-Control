import java.time.LocalDate
import java.time.ZoneOffset

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

val buildDateUtc = LocalDate.now(ZoneOffset.UTC).toString()

android {
    namespace = "cics.csup.qrattendancecontrol"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "cics.csup.qrattendancecontrol"
        minSdk = 23
        targetSdk = 34
        versionCode = 7
        versionName = "6.1"
        buildConfigField("String", "BUILD_DATE", "\"$buildDateUtc\"")
        resValue("string", "build_date", buildDateUtc)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    applicationVariants.all {
        if (buildType.name == "release") {
            outputs.all {
                @Suppress("DEPRECATION")
                val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                val resolvedVersion = versionName ?: versionCode.toString()
                outputImpl.outputFileName = "CICS_QR_Attendance_Control_${resolvedVersion}.apk"
            }
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Swipe Refresh
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Remote Config library
    implementation("com.google.firebase:firebase-config")

    // Push Notifications
    implementation("com.google.firebase:firebase-messaging")

    // Add this for In-App Messaging Display
    implementation("com.google.firebase:firebase-inappmessaging-display")

    // Analytics
    implementation("com.google.firebase:firebase-analytics")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}