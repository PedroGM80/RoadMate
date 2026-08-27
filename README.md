# RoadMate

A voice-controlled travel copilot for Android and Android Auto. You talk, it
listens, understands and answers out loud — all of it on-device. RoadMate
never sends your voice or your questions anywhere; the only network call in
the whole app is an optional weather lookup.

## What it does

- **Ask anything, hands-free.** Tap the mic, ask a question, get a spoken
  answer. Powered by on-device Gemini Nano (AICore) where available, with an
  honest "modo básico" fallback where it isn't.
- **"Llama a X"** — places a call directly, no dial-pad confirmation, by
  design: hands-free while driving. Ambiguous or missing contacts get a
  spoken explanation instead of a guess.
- **"Busca gasolineras", "hoteles cerca", "dónde hay un restaurante"** —
  hands the query straight to whatever Maps app is installed via a `geo:`
  intent, biased to the current location. RoadMate never queries a places
  API itself.
- **"Cuéntame un chiste"** — answered from a small local bank of original
  road-themed jokes, no network, no AI call.
- **Rest reminders.** A background silence detector notices long stretches
  without conversation and offers to suggest a break, spoken through TTS.
- **Android Auto.** Shows up as a Car App Library service (`POI` category)
  so the same assistant is reachable from the car's head unit.

## Why offline-first

Voice capture and interpretation stay on the device on purpose — this was a
hard requirement from day one, not a compromise. `SpeechRecognizer` handles
transcription locally, AICore/Gemini Nano handles the answer locally, and
`TextToSpeechManager` speaks it locally. The one optional exception is
weather, which is clearly disclosed on first run.

## Architecture

Clean Architecture across three Gradle modules, dependencies pointing
inward:

```
:app     Jetpack Compose UI, ViewModel, Android Auto Car App Library screens
:data    Repository implementations, ML managers, DataStore, Retrofit
:domain  Pure Kotlin — models, repository interfaces, use cases. No Android deps.
```

- **`:domain`** is a plain `kotlin("jvm")` module: `TravelContext`,
  `ContactLookupResult`, the repository interfaces, and the use cases
  (`GenerateResponseUseCase`, `DetectSilenceUseCase`, `RecordAudioUseCase`).
  `GenerateResponseUseCase` is the router: it checks call intent, then map
  search intent, then joke intent, and only falls through to Gemini if none
  of those match.
- **`:data`** implements every `:domain` repository interface
  (`di/RepositoryModule.kt` is the only place that knows both sides exist),
  plus the ML/system integrations: `GeminiNanoManager`, `SpeechRecognitionManager`,
  `TextToSpeechManager`, `AudioLevelDetector`, `CarMicrophonePreference`,
  `SilenceDetectionForegroundService`.
- **`:app`** is presentation-only: `RoadMateViewModel`, `HomeScreen`,
  `OnboardingScreen`, and the Car App Library screens under `car/`.

Dependency injection is Hilt end to end (`@HiltAndroidApp`, `@AndroidEntryPoint`,
`@HiltViewModel`, `@Binds`/`@Module`/`@InstallIn(SingletonComponent::class)`).

## Stack

- Kotlin, Jetpack Compose (Material 3), Coroutines/Flow
- Hilt for DI
- `com.google.ai.edge.aicore` — on-device Gemini Nano
- `android.speech.SpeechRecognizer` — on-device STT
- Android `TextToSpeech` — on-device TTS
- Retrofit/Moshi/OkHttp — the one optional network call, for weather
- DataStore Preferences — onboarding persistence
- `androidx.car.app` — Android Auto integration
- JUnit4 + `kotlinx-coroutines-test` — hand-rolled fakes, no mocking library

## Requirements

- minSdk 31, targetSdk/compileSdk 37 (AICore requires 31+)
- Optional: an `OPENWEATHER_API_KEY` in `local.properties` (gitignored). If
  absent, `WeatherDataSource` treats it as "weather unavailable" and skips
  the network call — nothing else in the app depends on it.

## Building and testing

```
./gradlew :app:installDebug          # build and install on a connected device/emulator
./gradlew :domain:test :app:testDebugUnitTest   # unit tests (both use hand-rolled fakes)
```

Android Auto behavior (the Car App Library screens) has only been verified
by compiling against the real `androidx.car.app` 1.7.0 API — Desktop Head
Unit could not be driven from this environment. Verify on a real head unit
or DHU before relying on it.

See [`PROGRESS.md`](PROGRESS.md) for what's built, what's been verified,
and what's still open, and [`FUTURE.md`](FUTURE.md) for ideas and known
gaps for the next round of work.
