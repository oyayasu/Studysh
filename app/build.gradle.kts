plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.oyayasu.studysh"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.oyayasu.studysh"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.10.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        // Piper and the Java ONNX API use the SAME pinned ORT binary (see workflow).
        jniLibs.pickFirsts += "**/libonnxruntime.so"
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("dev.ihorshevchuk.piper:piper-player:1.0")
    implementation("dev.ihorshevchuk.piper:piper-engine:1.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
