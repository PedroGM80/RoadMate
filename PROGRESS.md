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
- **Map search ("busca gasolineras", "hoteles cerca")**: shown on RoadMate's
  own downloaded offline map. (This used to hand off to the device's Maps app
  via a `geo:` intent; all external-Maps handoffs were removed on 2026-09-03
  at the user's request — "el mapa de Google no debe usarlo". A category
  becomes a POI filter, anything else is matched by name against the
  downloaded tiles, and with no region downloaded RoadMate says so rather
  than falling back to another app.)
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
- **First on-device run** (Xiaomi Redmi Note 14, HyperOS/Android 16, arm64,
  **no AICore**): the local-AI path was producing garbage (echoing the
  question). Root causes found and fixed — Qwen-0.5B is too weak and was
  getting a raw non-ChatML prompt; the prompt fed conversation history back
  recursively; MediaPipe returns UTF-8 as Latin-1. Now: model swapped to
  Qwen2.5-**1.5B** (via `local.properties`), prompt restructured
  (instruction first, question last before a "Respuesta:" cue, one recent
  turn max), mojibake round-tripped, model **warmed at startup** (first
  question ~4 s vs ~11 s cold). Arithmetic + question-mark + weather
  shortcuts added. Vosk small ES model mis-hears ~1 in 3 — bigger model is
  the open item. Map: POI filter pins now work (`querySourceFeatures` on the
  `poi` layer, not `queryRenderedFeatures`), +/- and recenter controls,
  street-level default zoom. Debug tracing to a file (`DebugTrace`) is in
  place on `develop` and must be stripped before merge. See `NEXT_SESSION.md`.


## Audit + hardening pass, 2026-09-02

A full read of every file, with fixes applied on `fix/audit-2026-09-02`.
Nothing here was found by running the app — there was no device in the loop —
so all of it still wants a device pass. What made it possible to be confident
anyway: `:domain` was compiled and its tests actually run (114 passing), and
every file was checked with a Kotlin parser and a missing-import pass.

### The one that mattered most

**Spanish never matched the parsers.** Java's regex engine defines `\b`, `\w`
and `\W` over ASCII unless told otherwise, so any pattern touching an accented
letter silently failed:

- `qu[eé]\b` never matched "qué hora es" — the most common way a Spanish
  driver starts a question. Nor "por qué", "quién", "cómo", "cuándo".
- `\b[uú]ltim[oa]\b` never matched "la última", so the call follow-up
  ("¿cuál?" → "la última") did nothing.
- `split(Regex("\\W+"))` turned "Ana García" into "ana", "garc", "a" — so
  picking a contact by an accented surname failed, as did recalling a place.
- `IGNORE_CASE` alone is ASCII-only case folding, so "QUÉ" ≠ "qué".

Three `:domain` tests were already failing on `develop` for exactly this.
Every pattern that reads the driver now goes through `spanishRegex()`, which
prepends Java's `(?U)`; `SpanishRegexTest` locks it down.

### Privacy

`backup_rules.xml` / `data_extraction_rules.xml` were the empty Android Studio
templates, so with `allowBackup="true"` the memory DB (conversation history,
home/work coordinates, relationships) and the `DebugTrace` file (full
transcripts) were eligible for Google Drive backup and phone-to-phone
transfer. Both now exclude them, along with the model and tile caches.

### Correctness

- One "oye copiloto" fired the wake handler repeatedly — Vosk keeps the
  hypothesis across partials, so every following partial still contained it.
- The mic was handed from the wake recognizer to Vosk with `cancel()` rather
  than `cancelAndJoin()`, so both briefly owned it.
- `AudioLevelDetector.stop()` released the `AudioRecord` from the caller's
  thread while `read()` was still blocked inside the native call.
- `SilenceDetectionForegroundService` went on to open the mic after a failed
  `startForeground` (a missing `return`).
- `MapView.onDestroy()` ran twice on activity finish.
- The Android Auto screen could hang on the host's loading spinner forever —
  the only thing that cleared it was the happy path.
- `TextToSpeechManager` mutated a plain list/map/flag from three threads, and
  followed the device locale, so an English-locale phone read Spanish text
  with an English voice.
- `GeminiRepositoryImpl`'s answer cache was unsynchronised and unbounded, with
  a key that changes on every question (the prompt embeds clock + GPS).
- `GeminiNanoManager` cached a single timeout as "AICore absent", permanently
  demoting a capable phone to "modo básico" for the session.
- A failed answer left the driver in silence.

### Latency, memory, battery

- Weather was fetched on the critical path of *every* answer, with OkHttp's
  default 10s/10s/10s timeouts and no cache. Now 4s-capped and cached for
  10 minutes / ~5 km.
- `onTrimMemory` / `onLowMemory` now release the local model (0.5–1.6 GB, by
  far the largest thing in the process) instead of waiting to be killed.
- `LocalLlmManager.isReady()` ran on the single inference thread, queueing
  behind whatever generation held it.
- `AudioLevelDetector` ran at 44.1 kHz to compute one RMS figure per buffer,
  for the whole trip. Now 16 kHz, matching Vosk and the wake word.
- The `MapView` was destroyed and rebuilt on every Voz/Mapa switch — GL
  context torn down, style re-fetched. It's now held above the Crossfade.

### Calls — the one irreversible thing RoadMate does

`ACTION_CALL` dials with no confirmation, by design. The lookup pasted the
transcript into a `DISPLAY_NAME LIKE '%name%'` pattern, so a stray `%` matched
every contact and the first row was dialled. It now uses the platform's own
`Phone.CONTENT_FILTER_URI`, ranks matches (exact > word-prefix > substring),
and only dials by itself when the best tier names one person. That rule lives
in `:domain` as `ContactMatching`, with tests. `Phone.LABEL` is read too, so a
number the contact named themselves ("Coche") can be offered and picked.

### Phrasings that were advertised but didn't work

Found by running every parser over a battery of realistic driver utterances:
"con más detalle" / "sé más breve" (the answer-length setting only matched if
the sentence contained the word "respuestas"), "pon música" with no app named,
"marca el número de X" / "ponme con X", and needs stated as needs ("tengo
hambre", "necesito echar gasolina").

### Accessibility

Contrast computed for every scheme pair. The signal amber was 4.18:1 on white
— under AA, and it's the mic CTA's fill and the "Escuchando…" line. `outline`
(chip/radio/switch boundaries) was 1.69:1 light and 1.72:1 dark against
WCAG 1.4.11's 3:1. All fixed; everything else already passed. The mic button
also used to move ~30dp down the moment it was tapped.

### Still not verified here

`:data` and `:app` were never compiled — Maven Central is unreachable from
that environment, so only `:domain` could be built and run. **Run
`./gradlew :domain:test :app:testDebugUnitTest` and a device pass before
trusting any of it.**

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
- **Intent parsing against real phrasing**: now probed systematically (see
  the 2026-09-02 audit below) rather than assumed, and the gaps that pass
  found are closed and tested. Still hand-written regex, and still only
  checked against phrasings someone thought of — a real device session with a
  real voice will find more.

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
