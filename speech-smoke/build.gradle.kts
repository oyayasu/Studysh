plugins { id("org.jetbrains.kotlin.jvm") }

kotlin {
    jvmToolchain(17)
    sourceSets.main {
        // Test the exact inference/download code compiled into the APK.
        kotlin.srcDir("../app/src/main/java/com/oyayasu/studysh/supertonic")
    }
}
dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime:1.31.0")
    implementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
tasks.test {
    useJUnit()
    maxHeapSize = "2g"
    inputs.property("modelDirectory", providers.environmentVariable("SUPERTONIC_MODEL_DIR").orElse(""))
    testLogging { events("passed", "skipped", "failed"); showStandardStreams = true }
}
