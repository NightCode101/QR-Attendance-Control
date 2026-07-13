import java.time.LocalDate
import java.time.ZoneOffset
import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.serialization)
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
        targetSdk = 35
        versionCode = 18
        versionName = "8.0.0"

        buildConfigField("String", "BUILD_DATE", "\"$buildDateUtc\"")
        resValue("string", "build_date", buildDateUtc)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                debugSymbolLevel = "full"
            }
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
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
                val outputImpl = this as BaseVariantOutputImpl
                val resolvedVersion = versionName ?: versionCode.toString()
                outputImpl.outputFileName = "CICS_QR_Attendance_Control_${resolvedVersion}.apk"
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Core UI & Architecture
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.config)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.inappmessaging.display)
    implementation(libs.firebase.dataconnect)
    implementation(libs.kotlinx.serialization.json)

    // Camera & Scanning
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.barcode.scanning)

    // External Tools
    implementation(libs.mp.android.chart)

    // AdMob & Consent
    implementation(libs.play.services.ads)
    implementation("com.google.android.ump:user-messaging-platform:2.2.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
