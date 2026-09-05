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

// Release signing. Resolved from keystore.properties at the repo root
// (gitignored — copy keystore.properties.example) or, for CI, the matching
// ROADMATE_KEYSTORE_* environment variables. When neither is present the
// release build is simply left unsigned, so `assembleRelease` still runs for
// contributors and on CI without the secret. A signed AAB needs the file or
// the env vars.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propKey) ?: System.getenv(envKey)

val releaseStoreFile = signingValue("storeFile", "ROADMATE_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "ROADMATE_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "ROADMATE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "ROADMATE_KEY_PASSWORD")
val releaseSigningReady = releaseStoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

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

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")

            // R8 stays off until a release build has been verified on a real
            // device — MediaPipe / Vosk / MapLibre are JNI-heavy and their
            // reflection surface isn't fully covered by consumer rules yet.
            // proguard-rules.pro below already carries the -keep set so the
            // day this flips to `true` nothing has to be figured out again.
            optimization {
                enable = false
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // The all-ABI APK is ~250 MB (MediaPipe + Vosk + MapLibre native libs).
    // Per-ABI splits cut each install to roughly a third. No universal APK —
    // `installDebug` still picks the right split for the connected device,
    // and CI only needs one to build. Not on Play, so no versionCode offset.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = false
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

    lint {
        // Pulls :data into the same analysis, which is where most of what
        // lint has to say about this app lives (mic and permission handling,
        // foreground services, resource leaks).
        //
        // It does not reach :domain: that's a plain kotlin("jvm") module, and
        // a JVM project only contributes a lint model when the
        // `com.android.lint` plugin is applied to it. :domain is pure logic
        // with no Android API surface, so there is little for lint to say
        // there — but the gap is real, not covered by this line.
        checkDependencies = true

        // A gate: the first run was triaged 2026-09-05 — the real findings
        // fixed (StringFormatInvalid, telephony uses-feature, dead SDK_INT
        // guards, redundant label), the rest parked in lint-baseline.xml. A
        // new warning outside the baseline now fails the build.
        abortOnError = true
        baseline = file("lint-baseline.xml")

        // Missing translations aren't a defect — the app is Spanish-only.
        disable += "MissingTranslation"
        // RoadMate logs through android.util.Log by design — Timber would be a
        // dependency added for nothing but this check.
        disable += "LogNotTimber"

        htmlReport = true
        sarifReport = true
        textReport = false
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
    implementation(libs.androidx.car.app.projected)
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
    androidTestImplementation(libs.androidx.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.car.app.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
