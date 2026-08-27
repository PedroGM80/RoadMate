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

android {
    namespace = "dev.pgm.roadmate.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 31 // com.google.ai.edge.aicore requires 31+
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "OPENWEATHER_API_KEY", "\"$openWeatherApiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.hilt.android)
    "ksp"(libs.hilt.compiler)

    implementation(libs.aicore)
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

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
