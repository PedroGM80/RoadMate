import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt.android)
}

// OPENWEATHER_API_KEY is read from local.properties (gitignored, per-machine)
// so the key never lands in source control. Absent by default; WeatherDataSource
// treats a blank key as "weather unavailable" and skips the network call.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val openWeatherApiKey: String = localProperties.getProperty("OPENWEATHER_API_KEY", "")

// Picovoice AccessKey for the Porcupine wake-word engine ("RoadMate", hands
// free). Free from console.picovoice.ai; per-machine, gitignored. Blank by
// default — WakeWordDetector treats a blank key (or a missing .ppn asset) as
// "wake word unavailable" and the app falls back to the mic button only.
val picovoiceAccessKey: String = localProperties.getProperty("PICOVOICE_ACCESS_KEY", "")

// The universal local-AI fallback model. Downloaded at runtime (Wi-Fi only,
// on explicit opt-in) by LocalAiModelManager and run through MediaPipe.
// Defaults to Qwen2.5-0.5B-Instruct q8 (~547 MB, Apache-2.0, ungated on
// Hugging Face) so the feature works out of the box on any device with no
// setup. Override any of these in local.properties to ship a different
// model (e.g. a larger Qwen, or a Gemma .task from your own host / with an
// HF token). A blank URL disables the download path entirely.
val localAiModelUrl: String = localProperties.getProperty(
    "LOCAL_AI_MODEL_URL",
    "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +
        "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
)
val localAiModelFilename: String =
    localProperties.getProperty("LOCAL_AI_MODEL_FILENAME", "qwen2.5-0.5b-instruct-q8.task")
// Expected byte size for an integrity check after download. 0 = unknown/skip.
val localAiModelSizeBytes: String =
    localProperties.getProperty("LOCAL_AI_MODEL_SIZE_BYTES", "546660344")

android {
    namespace = "dev.pgm.roadmate.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 31 // com.google.ai.edge.aicore requires 31+
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "OPENWEATHER_API_KEY", "\"$openWeatherApiKey\"")
        buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"$picovoiceAccessKey\"")
        buildConfigField("String", "LOCAL_AI_MODEL_URL", "\"$localAiModelUrl\"")
        buildConfigField("String", "LOCAL_AI_MODEL_FILENAME", "\"$localAiModelFilename\"")
        buildConfigField("long", "LOCAL_AI_MODEL_SIZE_BYTES", "${localAiModelSizeBytes}L")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

ksp {
    // Exported schemas make future Room migrations reviewable in git.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.hilt.android)
    "ksp"(libs.hilt.compiler)

    // On-device memory: conversation history + durable facts about the driver.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    "ksp"(libs.androidx.room.compiler)

    implementation(libs.aicore)
    // Universal local-AI fallback: a small model downloaded at runtime over
    // plain HTTPS (no account/token) and run on-device via MediaPipe.
    implementation(libs.mediapipe.tasks.genai)
    // Offline Spanish speech-to-text (Kaldi). Works with no Google speech
    // pack — the model is bundled in :app assets.
    implementation(libs.vosk.android)
    // Wake-word detection ("RoadMate", hands-free). On-device, no network.
    // Needs a Picovoice AccessKey (PICOVOICE_ACCESS_KEY in local.properties)
    // and a trained keyword .ppn in assets; absent either, it no-ops.
    implementation(libs.porcupine.android)
    implementation(libs.play.services.location)

    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.moshi.kotlin)
    "ksp"(libs.moshi.kotlin.codegen)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
