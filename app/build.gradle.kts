import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.hilt.android)
}

// Crashlytics is wired only when app/google-services.json is present. That
// file is gitignored — drop in your own Firebase config to turn crash
// reporting on. No file, no Firebase, and the build stays green (same
// graceful-degradation pattern as the optional OpenWeather API key).
val firebaseEnabled = file("google-services.json").exists()
if (firebaseEnabled) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// Per-machine overrides (gitignored). Only MAP_STYLE_URL is read here today.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// The Vosk offline Spanish speech model (~39 MB, Apache-2.0). Fetched into
// assets at build time so voice recognition works fully offline from the
// first launch — no Google speech pack, no runtime download, no account.
// The .zip is gitignored; VoskSpeechRecognizer unpacks it on device.
val downloadVoskModel = tasks.register("downloadVoskModel") {
    description = "Downloads the Vosk offline Spanish speech model into assets"
    // Resolve everything to plain serializable values so the task action is
    // safe under Gradle's configuration cache (no script/project references).
    val url = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
    val target = layout.projectDirectory
        .file("src/main/assets/vosk-model-small-es-0.42.zip").asFile
    outputs.file(target)
    onlyIf { !target.exists() }
    doLast {
        target.parentFile.mkdirs()
        println("Downloading Vosk ES model → $target")
        URI(url).toURL().openStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        check(target.length() > 10_000_000L) {
            "Vosk model download looks truncated (${target.length()} B)"
        }
    }
}

tasks.named("preBuild") { dependsOn(downloadVoskModel) }

android {
    namespace = "dev.pgm.roadmate"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.pgm.roadmate"
        minSdk = 31 // com.google.ai.edge.aicore requires 31+; on-device Gemini Nano is unavailable below it anyway
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // In-app map (MapLibre) tile + style source. OpenFreeMap is free,
        // keyless and no-account; override in local.properties to point at
        // MapTiler / a self-host without touching code.
        val mapStyleUrl = localProperties.getProperty(
            "MAP_STYLE_URL",
            "https://tiles.openfreemap.org/styles/liberty",
        )
        buildConfigField("String", "MAP_STYLE_URL", "\"$mapStyleUrl\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    // Crash reporting only — no Analytics. Pulled in only when Firebase is
    // configured (see the firebaseEnabled check above).
    if (firebaseEnabled) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.crashlytics)
    }

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.car.app)
    implementation(libs.maplibre.android.sdk)
    implementation(libs.maplibre.annotation)
    implementation(libs.hilt.android)
    "ksp"(libs.hilt.compiler)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}