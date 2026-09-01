# Progress

Status snapshot of RoadMate. Update this alongside feature work rather than
letting it drift — it's meant to answer "what's actually done and verified"
without reading the full git log.

## Done and verified live (emulator or real API)

- **Core voice loop**: mic → Vosk (on-device, model bundled) →
  `GenerateResponseUseCase` → Gemini Nano / downloaded model / local fast
  path → `TextToSpeech` (on-device). The STT half was rebuilt on Vosk
  (`VoskSpeechRecognizer`) after Android's `SpeechRecognizer` +
  `EXTRA_PREFER_OFFLINE` proved unreliable on devices without a Google
  speech pack (e.g. many Xiaomi/MIUI). Now streams live partial results to
  the UI and speaks a clear error instead of silently resetting. Build- and
  unit-test-verified; **not** yet run on hardware.
- **Clean Architecture split**: `:domain` / `:data` / `:app` Gradle modules,
  Hilt DI throughout.
- **On-device AI availability**: `GeminiNanoManager.checkAvailability()` and
  an honest UI label ("IA local activa" / "Modo básico"). Confirmed via a
  real instrumented test that AICore is genuinely unavailable on the
  emulator ("AiCoreService: not found") and the 5s timeout+fallback path
  works (14ms completion, no hang).
- **Universal local-AI fallback (build-verified, download URL verified)**:
  for devices without AICore, `GeminiRepositoryImpl` routes AICore →
  downloaded model (MediaPipe `LocalLlmManager`) → canned "modo básico".
  `LocalAiModelManager` downloads `Qwen2.5-0.5B-Instruct` q8 (~547 MB,
  Apache-2.0) over plain HTTPS from Hugging Face's public CDN — **no
  account, no token** — resumable, Wi-Fi-only, with a size-integrity check;
  `RoadMateViewModel` auto-starts it the moment it sees no local backend,
  and `HomeScreen` renders the full `LocalAiStatus` machine (preparing /
  downloading % / waiting-for-Wi-Fi / failed+retry / ready). Verified: the
  default model URL returns HTTP 206 with range support and
  `x-linked-size == 546660344` (matches the built-in check);
  `:app:assembleDebug` + `:app:bundleDebug` pass; `:domain:test` and
  `:app:testDebugUnitTest` pass (20 + 11, incl. 3 new `RoadMateViewModelTest`
  cases for auto-download). **Not** yet run on hardware — see FUTURE.md.
- **In-app offline map (build- & unit-verified)**: new "Mapa" tab
  (`RootScreen` bottom nav) with a MapLibre vector map on OpenFreeMap tiles
  (no API key). `OfflineMapManager` wraps MapLibre `OfflineManager` for a
  "Descargar esta zona" region download with a progress chip; POI pins
  (gasolineras / hoteles / comida) come from `queryRenderedFeatures` on the
  rendered tiles (fully offline, no places API), tap → Google Maps
  navigation intent. `:app:assembleDebug` passes; 8 new unit tests
  (`OfflineMapStatusTest`, `MapViewModelTest`). **Not** run on a GL device —
  map rendering, offline download, and POI extraction are unverified on
  hardware.
- **"Abre Spotify / YouTube Music"** (build- & unit-verified): fourth local
  shortcut in `GenerateResponseUseCase`, ahead of Gemini. `MediaIntentParser`
  needs a launch verb *and* a known app name. `MediaRepositoryImpl` only
  fires the package's launcher intent — no playback control — and the reply
  says "Abriendo", not "reproduciendo"; missing app is handled. Manifest
  `<queries>` lists both packages. 6 new tests.
- **Adaptive `RootScreen`** (build-verified): `NavigationSuiteScaffold` — bar
  on a phone, rail when wider; past ~840dp both Voz and Mapa render side by
  side and the nav items drop. Phone behaviour unchanged (two tabs, one
  saved int). Not checked on a real tablet/foldable.
- **Answer-length preference** (build- & unit-verified): "respuestas cortas /
  con más detalle / normales" persists an `AnswerStyle` in DataStore, and
  `PromptBuilder` folds it into every Gemini prompt.
- **On-device memory** (build- & unit-verified; DAO not instrumented-tested):
  new `RoadMateDatabase` (Room, in `:data` — the dep moved from `:app` where
  it was unused). `GenerateResponseUseCase` now (a) gives real questions the
  last few Q&A exchanges for continuity and records the new pair, (b) takes
  "recuerda que… / prefiero… / olvida lo de… / ¿qué sabes de mí?" as
  PREFERENCE facts, (c) bumps a PLACE fact on every "busca X", (d) takes
  "esta es mi casa / aquí es mi trabajo" (HOME/WORK from the current
  location) and "X es mi hermano" (RELATIONSHIP) — after which "llama a mi
  hermano" resolves through memory. Preferences, frequent places and
  home/work coords are folded into every Gemini prompt. Also: keyword recall
  of past exchanges ("¿qué te dije sobre…?"), PLACE capture from the map's
  nav button too (with name normalisation), and an instrumented `MemoryDao`
  test. Replaces the vestigial `TravelContext.lastResponses`.
- **Design pass** (build-verified): completed theme (full brand
  `ColorScheme` + containers/outline, a real `Typography` scale, `Shapes`,
  a named `Spacing` grid); every UI literal in `strings.xml`, every fixed
  spoken line in one `SpokenText` object; `HomeScreen` split into
  single-purpose components under `presentation/components/` (shared
  `InfoPill` replaces two near-identical pills). Audit fixes: errors render
  in the error colour not reply-green, 48dp touch targets, a spinner during
  "Procesando…", sentence-case status, mic "stop" no longer the alarming
  red, decorative loops honour reduce-motion, `MapScreen` controls inset
  past the system bars with a real `ModalBottomSheet`, Voz/Mapa crossfade,
  and a `TopAppBar` with an overflow menu: theme (system / light / dark /
  auto-night), answer length (mirrors the voice command), "borrar mapas
  descargados", and "borrar lo aprendido" (confirm → `MemoryRepository.clearAll`).
- **Location + weather context**: `LocationRepository` (FusedLocationProvider)
  feeds `TravelContext`; weather is the one optional network call, disclosed
  on first run. `refreshLocation()` times out via
  `withTimeoutOrNull(Constants.LOCATION_TIMEOUT_MS)` instead of hanging.
- **Voice UX polish** (build-verified): a synthesised two-note earcon
  (`Earcon`, no bundled asset) on mic start/stop instead of the generic
  `ToneGenerator` beep; a same-session follow-up for an ambiguous "llama a
  X" ("la segunda" / a surname / "la del móvil" / "la del trabajo" — the
  last two label-aware via `PhoneLabel` off `Phone.TYPE`), state kept in
  `GenerateResponseUseCase`; a Quick Settings tile and a resizable
  home-screen widget (`RoadMateWidgetProvider`, RemoteViews) that both open
  straight into listening via `MainActivity.EXTRA_START_LISTENING`.
- **Rest reminders**: `SilenceDetectionForegroundService` +
  `AudioLevelDetector` detect long silences and speak a reminder in the
  background. Verified live via logcat/dumpsys — clean foreground-service
  start/stop cycle, correct `FOREGROUND_SERVICE_TYPE_MICROPHONE`.
  Silence monitoring only starts once `RECORD_AUDIO` is actually granted
  (was a real bug — AudioRecord failed silently before permission existed).
- **Onboarding**: first-run screen with value prop + driving-safety
  disclaimer, gated behind DataStore, verified via `pm clear` + relaunch.
- **Local jokes**: `JokeProvider` — 16 original road-themed jokes, no
  licensing risk (deliberately not scraped from the internet), bypasses
  Gemini entirely.
- **Phone calling ("llama a X")**: direct `ACTION_CALL`, no dial-pad
  confirmation — the user's explicit choice over a safer confirm-first
  alternative. Ambiguous/missing contact and missing-permission cases all
  get a spoken explanation instead of failing silently or guessing.
  Verified: full permission cascade (location → mic → notifications →
  contacts → call) live on-device, all four permissions ending up granted,
  no crash.
- **Map search ("busca gasolineras", "hoteles cerca")**: hands the query to
  the on-device Maps app via a `geo:` intent instead of RoadMate querying a
  places API — keeps the "your voice and questions never leave the phone"
  promise intact. Verified the intent resolves and hands off to Google Maps
  live on the emulator.
- **Android Auto integration**: Car App Library service registered under
  the `POI` category (changed from an earlier, less honest `IOT` choice).
  `HomeCarScreen` compiles against real `androidx.car.app` 1.7.0 APIs
  (`Header`, `PaneTemplate`, `Row.setImage`, `Action.setIcon`).
- **Car microphone preference**: `CarMicrophonePreference` uses
  `AudioManager.setCommunicationDevice()` (API 31+) to prefer the car's mic
  when connected — best-effort, stays 100% offline. (An earlier proposal to
  use Google Cloud Speech-to-Text for guaranteed car-mic capture was
  explicitly rejected — offline-only is a hard requirement, not a
  preference.)
- **UI/UX pass**: brand palette (RoadBlue/SignalAmber/ReplyGreen), custom
  launcher icon, M3-Expressive-style mic button (spring press + pulse +
  waveform), live-region announcements for TalkBack, location chip with
  retry-on-failure state.
- **Interaction polish**: haptic tick (`ToggleOn`/`ToggleOff`) plus a short
  `ToneGenerator` earcon on mic start/stop, a haptic "confirm" + spring
  "pop" on the response card when an answer lands, and a spoken time-of-day
  greeting once per calendar day (tracked via a single DataStore date
  string, gated behind core permissions being granted). All verified live
  on the emulator, including a fresh-install run confirming the greeting's
  `last_greeted_date` lands correctly in the DataStore file with no crash.
- **Test suite**: `:domain:test` and `:app:testDebugUnitTest`, hand-rolled
  fakes (no mocking library). Caught one real bug this way — a detached
  `.onEach{}.launchIn()` coroutine in `RoadMateViewModel` that let
  `startListening()` return before the response actually arrived; fixed by
  switching to direct `.collect{}`.
- **Per-ABI APK splits**: `splits.abi` on for `armeabi-v7a` / `arm64-v8a` /
  `x86_64`, no universal APK. `assembleDebug` produces three ~115–125 MB
  APKs instead of one ~250 MB all-ABI build; `installDebug` picks the match
  for the connected device. R8 stays off (`optimization.enable = false`) but
  `app/proguard-rules.pro` already holds the `-keep` set for the JNI-heavy
  libs (MediaPipe, Vosk, MapLibre, AICore) plus Crashlytics, so enabling it
  later is a one-line flip once a release build is checked on hardware.

## Explicitly unverified / open

- **Real Android Auto head unit / DHU**: never actually driven in this
  environment — Desktop Head Unit couldn't be gotten working here (the
  Android Auto companion app's developer-mode UI didn't resolve cleanly,
  and DHU opens a window outside the reach of the available tooling).
  Everything car-side is verified by compilation against the real API only.
  **Needs a real car or a working DHU setup before shipping.**
- **Play Store category for the Android Auto service**: `POI` is the best
  fit available today, but no category actually matches "general voice
  assistant" well. Talk to Google's Android for Cars team before
  submitting for review — a mismatched category risks rejection or later
  removal.
- **Privacy policy**: written up in `PRIVACY.md` (contact
  pedro13087@gmail.com), grounded in what the code actually does. Still
  needs to be *published* somewhere linkable (GitHub Pages / the Play
  Console listing) and kept in sync if data flows change.
- **Map search query parsing**: `MapSearchIntentParser` is a simple
  free-text regex match on "busca/encuentra X" and "dónde hay X". It hasn't
  been checked against a wide range of real phrasing — worth revisiting if
  false positives/negatives show up in practice (e.g. "busca información
  sobre..." would currently also match and get sent to Maps).

## Deliberate constraints (not gaps)

- **No cloud STT/TTS, ever. No inference off-device.** Voice capture,
  interpretation and answer generation are all on-device by hard
  requirement, restated explicitly after an earlier proposal (Cloud
  Speech-to-Text for car-mic capture) was rejected. No prompt or transcript
  is ever sent anywhere. The two content-free network calls are the
  optional, disclosed weather lookup and the one-time model-file download (a
  plain GET of a public openly-licensed file — no query data, no account).
- **Crash reporting is opt-in and diagnostics-only.** Firebase Crashlytics
  is wired but activates only when a build includes `app/google-services.json`
  (gitignored). When on, it uploads stack trace + device/app state after a
  crash — never voice, transcripts, answers, location or contacts. No
  Firebase Analytics. See `PRIVACY.md`.
- **No mocking library.** Test doubles are hand-rolled fakes, duplicated
  separately in `:domain/src/test` and `:app/src/test` (module test sources
  aren't cross-visible without a testFixtures setup, judged not worth the
  extra Gradle wiring for this size of project).
- **Spanish only.** Not a gap — the voice model (Vosk ES), the assistant's
  tone, the joke bank and every intent regex are Spanish, and the target
  user is Spanish-speaking. UI strings are in `strings.xml` so a `values-xx/`
  is cheap later, but a real other-language experience needs the whole voice
  layer and isn't planned.
