# Progress

Status snapshot of RoadMate. Update this alongside feature work rather than
letting it drift — it's meant to answer "what's actually done and verified"
without reading the full git log.

## Done and verified live (emulator or real API)

- **Core voice loop**: mic → `SpeechRecognizer` (on-device) → `GenerateResponseUseCase`
  → Gemini Nano or local fast path → `TextToSpeech` (on-device). Verified on
  a Medium_Phone (API 37) AVD end to end.
- **Clean Architecture split**: `:domain` / `:data` / `:app` Gradle modules,
  Hilt DI throughout.
- **On-device AI availability**: `GeminiNanoManager.checkAvailability()` and
  an honest UI label ("IA local activa" / "Modo básico"). Confirmed via a
  real instrumented test that AICore is genuinely unavailable on the
  emulator ("AiCoreService: not found") and the 5s timeout+fallback path
  works (14ms completion, no hang).
- **Location + weather context**: `LocationRepository` (FusedLocationProvider)
  feeds `TravelContext`; weather is the one optional network call, disclosed
  on first run. `refreshLocation()` times out via
  `withTimeoutOrNull(Constants.LOCATION_TIMEOUT_MS)` instead of hanging.
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
- **Privacy policy contact email**: the published privacy policy still has
  a placeholder (`[email de contacto pendiente de añadir]`) instead of a
  real contact address — needs a real value before this can go live.
- **Map search query parsing**: `MapSearchIntentParser` is a simple
  free-text regex match on "busca/encuentra X" and "dónde hay X". It hasn't
  been checked against a wide range of real phrasing — worth revisiting if
  false positives/negatives show up in practice (e.g. "busca información
  sobre..." would currently also match and get sent to Maps).

## Deliberate constraints (not gaps)

- **No cloud STT/TTS, ever.** Voice capture and interpretation are offline
  by hard requirement, restated explicitly after an earlier proposal
  (Cloud Speech-to-Text for car-mic capture) was rejected. Weather is the
  only network call anywhere in the app, and it's optional and disclosed.
- **No mocking library.** Test doubles are hand-rolled fakes, duplicated
  separately in `:domain/src/test` and `:app/src/test` (module test sources
  aren't cross-visible without a testFixtures setup, judged not worth the
  extra Gradle wiring for this size of project).
