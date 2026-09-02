# RoadMate

A voice-controlled travel copilot for Android and Android Auto. You talk, it
listens, understands and answers out loud — all of it on-device. RoadMate
never sends your voice or your questions anywhere. It reaches the network
for exactly two things: an optional weather lookup, and a one-time download
of the local-AI model file (a public, openly-licensed file — no account, no
query data). A Firebase-configured build additionally uploads a **crash
report** if the app crashes (diagnostics only — see [`PRIVACY.md`](PRIVACY.md));
builds without a `google-services.json` have no crash reporting.

## What it does

- **Ask anything, hands-free.** Tap the mic, ask a question, get a spoken
  answer. Powered by on-device Gemini Nano (AICore) where available. Where
  it isn't, RoadMate automatically downloads a small openly-licensed model
  (**Qwen2.5-0.5B-Instruct**, ~547 MB, Apache-2.0) over plain HTTPS —
  Wi-Fi only, no account, no sign-up — and runs it on-device through
  MediaPipe. It only drops to an honest "modo básico" canned reply while
  that download hasn't finished (or can't happen at all).
- **"Llama a X"** — places a call directly, no dial-pad confirmation, by
  design: hands-free while driving. Also "marca el número de X", "ponme con
  X", "quiero hablar con X". Several people with that name? It asks, and you
  finish with "la segunda" or a surname. One person with two numbers? It asks
  which, and you answer "el móvil" / "la del trabajo" — or whatever *you*
  called it in your contacts ("el coche"). Missing contact → a spoken
  explanation, not a guess.

  Because this is the one thing RoadMate does that can't be undone, the rule
  for *when* it's allowed to decide by itself is deliberately narrow and
  lives on its own in `:domain` (`ContactMatching`, with tests): an exact
  name beats a word-prefix beats a substring, only the best tier counts, and
  more than one person in that tier is always a question, never a guess.
- **"Busca gasolineras", "hoteles cerca", "dónde hay un restaurante"** —
  pinned on RoadMate's own downloaded map (see below), never handed to an
  external Maps app and never through a places API. **A need works too**:
  "tengo hambre", "necesito echar gasolina", "me estoy quedando sin
  gasolina", "quiero parar a descansar" find the same thing without having
  to phrase it as a search.
- **"Cuéntame un chiste"** — answered from a small local bank of original
  road-themed jokes, no network, no AI call.
- **"¿Qué tiempo hace?" / "¿va a llover?"** — answered straight from the
  weather lookup, not the AI model, so it's reliable even in "modo básico".
  Needs an `OPENWEATHER_API_KEY` in `local.properties`; without it RoadMate
  says plainly it can't check rather than guessing.
- **"Abre Spotify" / "pon música en YouTube Music"** — brings the music app
  to the foreground. Just opens it (no playback control) and says so; an
  app that isn't installed gets a spoken explanation. A bare **"pon música"**
  that names no app opens whichever known one you actually have.
- **"Respuestas cortas" / "con más detalle" / "sé más breve" / "normales"** —
  RoadMate remembers how long you like answers (across trips) and shapes
  every reply to match.
- **It remembers.** Replies build on the last few things said this trip.
  **"Recuerda que no me gustan las autovías"**, **"prefiero las
  nacionales"** → stored on-device and fed into every future answer;
  **"olvida lo de…"** drops it, **"¿qué sabes de mí?"** reads it back.
  **"Esta es mi casa"** / **"aquí es mi trabajo"** save the spot;
  **"Juan es mi hermano"** then makes **"llama a mi hermano"** work.
  **"¿Qué te dije sobre el hotel de Ronda?"** searches back through the
  conversation. Places you search for — by voice or by tapping the map —
  build up as context too. All of it in a local database, nothing sent
  anywhere.
- **In-app map ("Mapa" tab).** A real vector map (MapLibre + OpenFreeMap,
  OpenStreetMap data, no API key) showing your position. **"Descargar esta
  zona"** saves the visible area so the map keeps working with no
  connection. Filter chips drop offline pins for gasolineras / hoteles /
  comida, read straight from the downloaded tiles — no places API. Tapping a
  pin launches turn-by-turn in the Google Maps app.
- **Rest reminders.** A background silence detector notices long stretches
  without conversation and offers to suggest a break, spoken through TTS.
- **Android Auto.** Shows up as a Car App Library service (`POI` category)
  so the same assistant is reachable from the car's head unit.
- **Quick Settings tile + home-screen widget.** "Preguntar a RoadMate" in
  the pull-down, or a resizable home-screen widget, both open the app
  straight into listening.

## Why offline-first

Voice capture and interpretation stay on the device on purpose — this was a
hard requirement from day one, not a compromise. **Vosk** (Kaldi) transcribes
speech locally — its Spanish model is bundled, so it works with no Google
speech pack and no network — AICore/Gemini Nano (or the downloaded Qwen model
via MediaPipe) handles the answer locally, and `TextToSpeechManager` speaks
it locally. The one optional exception is weather, which is clearly disclosed
on first run. The one-time model download is a plain HTTPS GET of a public,
openly-licensed file from Hugging Face's CDN — it carries no query data and
needs no login.

The full data story — what stays on the device, the two times anything
leaves it, and every permission — is in [`PRIVACY.md`](PRIVACY.md).

That promise extends to Android's own backup: the memory database, the debug
trace and the model/tile caches are explicitly excluded from Auto Backup and
device-to-device transfer (`app/src/main/res/xml/backup_rules.xml` and
`data_extraction_rules.xml`). Only the harmless UI preferences restore.

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
  plus the ML/system integrations: `GeminiNanoManager`, `LocalAiModelManager`
  (HTTPS model download, resumable, Wi-Fi-gated), `LocalLlmManager`
  (MediaPipe LLM Inference), `VoskSpeechRecognizer`, `TextToSpeechManager`,
  `AudioLevelDetector`, `CarMicrophonePreference`,
  `SilenceDetectionForegroundService`. `GeminiRepositoryImpl` routes a
  prompt AICore → downloaded model → canned fallback.
- **`:app`** is presentation-only: `RoadMateViewModel`, `HomeScreen`,
  `OnboardingScreen`, `RootScreen` (Voz/Mapa — tabs on a phone, both panes
  side by side past ~840dp), the Car App Library screens
  under `car/`, and the in-app map under `presentation/map/`
  (`MapScreen`, `MapViewModel`, `OfflineMapManager` — MapLibre lives here,
  not in `:data`, to keep GL/UI types out of the clean-arch core).

Dependency injection is Hilt end to end (`@HiltAndroidApp`, `@AndroidEntryPoint`,
`@HiltViewModel`, `@Binds`/`@Module`/`@InstallIn(SingletonComponent::class)`).

## Stack

- Kotlin, Jetpack Compose (Material 3), Coroutines/Flow
- Hilt for DI
- `com.google.ai.edge.aicore` — on-device Gemini Nano
- `com.google.mediapipe:tasks-genai` — runs the downloaded model on-device,
  the universal local-AI fallback
- OkHttp — resumable HTTPS download of the ~547 MB model file
- `com.alphacephei:vosk-android` — offline Spanish STT (model bundled in
  assets, fetched at build time by the `downloadVoskModel` task)
- `org.maplibre.gl:android-sdk` + OpenFreeMap tiles — in-app vector map with
  downloadable offline regions, no API key (`MAP_STYLE_URL` overridable in
  `local.properties`)
- Android `TextToSpeech` — on-device TTS
- [Lucide](https://lucide.dev) icons (ISC) — the handful the UI uses are
  vendored as `<vector>` drawables (`app/.../res/drawable/lucide_ic_*`, see
  `third_party/lucide/`) instead of a dependency; no Material Icons artifact
- Retrofit/Moshi/OkHttp — the one optional network call, for weather
- DataStore Preferences — onboarding + answer-style persistence
- Room — the on-device memory DB (conversation history + driver facts)
- `androidx.car.app` — Android Auto integration
- Firebase Crashlytics — optional, opt-in via `app/google-services.json`;
  crash diagnostics only, no Analytics
- JUnit4 + `kotlinx-coroutines-test` — hand-rolled fakes, no mocking library

## Requirements

- minSdk 31, targetSdk/compileSdk 37 (AICore requires 31+)
- Optional: an `OPENWEATHER_API_KEY` in `local.properties` (gitignored). If
  absent, `WeatherDataSource` treats it as "weather unavailable" and skips
  the network call — nothing else in the app depends on it.
- Optional: `app/google-services.json` (gitignored) turns on Firebase
  Crashlytics. Without it the `google-services` / `crashlytics` Gradle
  plugins aren't applied and the Firebase SDK isn't linked — the build is
  unaffected. Crash reports are diagnostics only; see `PRIVACY.md`.
- The local-AI model needs **no setup**. `LocalAiModelManager` downloads
  `Qwen2.5-0.5B-Instruct` q8 (`~547 MB`, Apache-2.0) from Hugging Face's
  public CDN on first run on a non-AICore device. To ship a different model,
  set `LOCAL_AI_MODEL_URL` / `LOCAL_AI_MODEL_FILENAME` /
  `LOCAL_AI_MODEL_SIZE_BYTES` in `local.properties` (a blank URL disables
  the download path entirely).

## Building and testing

```
./gradlew :app:installDebug          # build and install on a connected device/emulator
./gradlew :domain:test :app:testDebugUnitTest   # unit tests (both use hand-rolled fakes)
./gradlew :app:lintDebug             # Android Lint (:app + :data; see app/build.gradle.kts)
```

That's the whole setup — the model download happens at runtime, so a plain
`installDebug` on a non-AICore device (on Wi-Fi) reaches "IA local activa"
on its own after the ~547 MB fetch. AICore devices show it straight away.

Android Auto behavior (the Car App Library screens) has only been verified
by compiling against the real `androidx.car.app` 1.7.0 API — Desktop Head
Unit could not be driven from this environment. Verify on a real head unit
or DHU before relying on it.

See [`PROGRESS.md`](PROGRESS.md) for what's built, what's been verified,
and what's still open, and [`FUTURE.md`](FUTURE.md) for ideas and known
gaps for the next round of work.
