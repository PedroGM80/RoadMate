# RoadMate R8 / ProGuard keep rules.
#
# NOT active yet: `android.buildTypes.release.optimization.enable` is `false`,
# so nothing here is applied. It lives here so that turning R8 on (once a
# release build has been checked on a real device) is a one-line change with
# the native-library keep set already worked out.
#
# Everything RoadMate keeps is here because the library talks to native code
# over JNI or resolves classes reflectively — R8 can't see those edges.

# --- MediaPipe LLM Inference (com.google.mediapipe:tasks-genai) -------------
# JNI bridge + Guava/AutoValue generated types reached only from native.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
-keep class com.google.common.** { *; }
-dontwarn com.google.common.**
-dontwarn javax.lang.model.element.Modifier

# --- AICore / on-device Gemini Nano (com.google.ai.edge.aicore) ------------
-keep class com.google.ai.edge.aicore.** { *; }
-dontwarn com.google.ai.edge.aicore.**

# --- Vosk / Kaldi offline STT (com.alphacephei:vosk-android) --------------
# The Java API is a thin JNA-backed wrapper over libvosk.so.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# --- MapLibre GL (org.maplibre.gl:android-sdk) ---------------------------
# Native renderer callbacks resolve these by name.
-keep class org.maplibre.android.** { *; }
-keep class org.maplibre.geojson.** { *; }
-dontwarn org.maplibre.**

# --- Firebase Crashlytics -----------------------------------------------
# Keep line numbers / source file so stack traces stay readable, and don't
# strip the Crashlytics-tagged exception types.
-keepattributes SourceFile,LineNumberTable
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# --- Moshi (weather DTOs in :data) -------------------------------------
# The generated JsonAdapters are kept by Moshi's own consumer rules; this is
# a belt-and-braces keep for the reflective fallback.
-keep class dev.pgm.roadmate.data.datasource.remote.** { *; }

# --- Kotlin / coroutines reflective bits ------------------------------
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
