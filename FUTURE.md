# Future improvements

Ideas and known gaps that came up while building RoadMate but aren't done.
Roughly ordered easiest/highest-impact first within each section — not a
commitment, just a starting point for the next session. See
[`PROGRESS.md`](PROGRESS.md) for what's already shipped and verified.

## Quick wins (small effort, real impact)

- **Wider intent coverage — a round done 2026-09-02.** Every parser was run
  over a battery of realistic driver utterances rather than the phrasings it
  was written against, which turned up four gaps (three of them advertised in
  the README): "con más detalle" / "sé más breve", a bare "pon música" with
  no app named, "marca el número de X" / "ponme con X", and needs stated as
  needs ("tengo hambre", "necesito echar gasolina"). All closed and tested.

  Also fixed the reason most of the accented phrasings never matched at all:
  Java's `\b` and `\W` are ASCII-only, so "qué", "última" and "García" fell
  through everywhere. See `SpanishRegex.kt`.

  Still hand-written regex, and still only checked against phrasings someone
  thought of — a device session with a real voice will find more.
- **Voice search → in-app offline map — done 2026-09-03.** All external-Maps
  (`geo:` / `google.navigation:`) handoffs were removed; a category becomes a
  POI filter on RoadMate's own downloaded tiles, anything else is matched by
  name, and no downloaded region gets an honest "no tengo un mapa descargado
  de esta zona" rather than a fallback to another app.
- **Contact follow-up: label-aware — done.** `ContactMatch` now carries a
  `PhoneLabel` (mobile / work / home / main / other) read from
  `Phone.TYPE`. One contact with two labelled numbers is treated as
  ambiguous ("Ana tiene varios números: el móvil o el del trabajo. ¿Cuál?")
  and `CallFollowUpParser` resolves "el móvil" / "la del trabajo" / "la de
  casa". Open: custom labels (`Phone.LABEL` free text) aren't matched, only
  the standard types.

## Medium effort

- **Q&A latency — stream the answer to TTS.** Measured on-device (Xiaomi,
  no AICore, Qwen2.5-1.5B on CPU): ~7 s from "stop talking" to "hear the
  answer" warm, ~12 s cold. Startup warm-up already removed the cold
  penalty. Biggest remaining win: `LlmInferenceSession.generateResponseAsync`
  + a progress listener, buffer to sentence boundaries, feed
  `TextToSpeechManager` incrementally so it starts speaking at ~1.5 s
  instead of waiting ~6 s for the whole reply. Smaller: drop lat/lon +
  weather lines from the prompt when the question doesn't need them;
  tighten Vosk end-of-speech. GPU backend is a dead end on MediaTek
  (silent hang).
- **Bigger Vosk model.** The bundled `vosk-model-small-es-0.42` mis-hears
  roughly 1 utterance in 3 on device (drops accents, "que este" for
  "quince"). `vosk-model-es-0.42` (~1.4 GB) is much better — needs a
  runtime download path like the LLM has (the small one is bundled in
  assets). Improves accuracy, not latency.

- **Voice personality — done, but keep tuning.** One tone now runs through
  `GEMINI_SYSTEM_PROMPT` and every fixed spoken line (calls, map, media,
  rest reminders, greeting, errors): "calm co-driver, short sentences, no
  filler". Worth revisiting once it's heard on a device — TTS cadence can
  make a written line land differently — and the joke bank still has its
  own separate voice.
- **Adapt to the driver (continued).** Real weight-level learning isn't on
  the table — AICore is frozen, MediaPipe LLM Inference is inference-only,
  and anything server-side breaks the "nothing leaves the phone" stance.
  Shipped: `RoadMateDatabase` (Room); conversation-history continuity;
  `PREFERENCE` / `PLACE` / `HOME` / `WORK` / `RELATIONSHIP` facts (the last
  wired into call resolution); keyword recall of past exchanges ("¿qué te
  dije sobre…?"); PLACE capture from both voice search and the map nav
  button, with name normalisation; an instrumented `MemoryDao` test. All
  facts fold into the prompt. Still open:
  - *STT vocabulary.* Vosk takes a phrase-list grammar at `Recognizer(...)`;
    feeding it contact names + frequent place names sharply improves
    recognition of exactly those. **Careful:** a phrase-list grammar
    *restricts* the decoder to those phrases, so it can't be used for the
    dictation path without breaking free-form questions. It would need a
    second, command-only recognizer, or a post-hoc fuzzy match of the
    transcript against the driver's actual contacts. The latter is testable
    without a device and is probably where to start.
  - *Feedback → few-shot.* Let the driver react ("no era eso"); store
    corrections locally and prepend a couple as guidance. (The length half —
    "más corto" — now works as a persisted setting, see
    `StylePreferenceParser`.)
  - *Better base model (build-time).* `LOCAL_AI_MODEL_URL` is overridable —
    LoRA-finetune Qwen2.5-0.5B on a driving-assistant set offline, merge,
    convert to `.task`, ship that URL.
- **Home screen widget — done.** `RoadMateWidgetProvider` (plain
  `AppWidgetProvider`, RemoteViews, no Glance dependency) puts the same
  one-tap-to-listen action on the home screen: brand-blue pill, mic icon +
  "Preguntar", resizable, `updatePeriodMillis=0` (nothing to refresh). Taps
  fire `MainActivity` with `EXTRA_START_LISTENING`, same path as the tile.
  Not yet checked on a device.
- **Accessibility — contrast measured 2026-09-02, the rest still open.**
  Every colour-scheme pair was computed against WCAG 2.1 and three failures
  fixed (the signal amber at 4.18:1, and `outline` at 1.69:1 light / 1.72:1
  dark against 1.4.11's 3:1). The mic button also used to shift ~30dp the
  moment it was tapped, which is a moving target for a driver's thumb.

  Still open: no Switch Access pass, no font-scale check (the layout uses
  fixed `dp` heights in places and has never been seen at 200% text), and
  Android Auto's own accessibility surface is untouched.
- **Settings surface — extend it.** The `TopAppBar` overflow now covers
  theme (system / light / dark / auto-night), answer length, "borrar mapas
  descargados" and "borrar lo aprendido". If it grows further it wants a
  real settings screen. `AUTO` now flips on real sunrise/sunset
  (`SolarClock`, standard low-precision equation, unit-tested incl. the
  polar cases) using the last location fix, and only falls back to the
  fixed 20:00–07:00 window when there's no fix yet. Open: the theme is
  computed at composition time, so it won't switch mid-session exactly at
  dusk without a recomposition.
- **Real mic-reactive waveform — resolved by removal.** The timer-driven
  5-bar waveform was dishonest (nothing tapped the mic amplitude), so
  `MicButton` now shows a single breathing dot while listening. A genuine
  amplitude tap is still possible later, but it needs an `AudioRecord`
  path that doesn't fight Vosk for the mic — a device job, not done here.

## Larger initiatives

- **Vosk STT: verify on hardware + trim size.** The switch from Android
  `SpeechRecognizer` to Vosk (`VoskSpeechRecognizer`, `vosk-model-small-es-0.42`
  bundled in assets) compiles, packages, and is unit-tested, but hasn't run
  on a device — needs a real mic pass (partials, end-pointing, the
  no-speech timeout, the mic-permission error path).

- **In-app map: verify on a GL device + polish.** `MapScreen` / MapLibre /
  `OfflineMapManager` compile and unit-test, but nothing map-side has run on
  hardware: needs a real GL surface to confirm the OpenFreeMap style renders,
  the blue-dot location component activates (MapLibre's default location
  engine — may need `play-services-location` wired as the engine), the
  "Descargar esta zona" flow completes and survives airplane mode, and that
  `queryRenderedFeatures(... "class")` actually matches the OpenFreeMap
  "liberty" POI layers (the schema/layer names are assumed, not checked).
  Also: the `google.navigation:` intent needs an `ActivityNotFoundException`
  guard tested; consider a "borrar mapas descargados" action; Android Auto
  still has no map surface.

- **APK size / ABI splits.** Per-ABI splits are now on (`armeabi-v7a`,
  `arm64-v8a`, `x86_64`, no universal APK) — each install is ~115–125 MB
  instead of one ~250 MB all-ABI build. Still large: the 39 MB Vosk model
  ships in assets (same in every split) and MediaPipe's native libs are
  bulky. Next steps to shrink the base install: move the Vosk model to a
  runtime download, swap `vosk-model-small` for something smaller, and ship
  an `.aab` if this ever goes to Play. `proguard-rules.pro` now carries the
  native-lib `-keep` set, but R8 stays off
  (`optimization.enable = false`) until a release build is verified on a
  device.

- **Verify on a real Android Auto head unit or working DHU.** Still the
  single biggest unverified risk — everything car-side is only checked by
  compiling against the real `androidx.car.app` API, never actually driven.
  Blocks confidently shipping the Android Auto surface.
- **Verify AICore/Gemini Nano on real hardware.** Confirmed *absent* on the
  emulator (expected) but never confirmed *present and working* on a real
  AICore-capable device — the "IA local activa" path is unverified end to
  end.
- **Verify the local-model download + inference on a real device.** The
  fallback (`LocalAiModelManager` HTTPS download → `LocalLlmManager`
  MediaPipe) compiles, the unit tests cover the auto-trigger, and the
  default model URL is confirmed reachable with no auth (HTTP 206, range
  supported, `x-linked-size` matches the built-in integrity check). But it
  has not been run on hardware: needs a non-AICore device (e.g. the Xiaomi)
  to confirm the ~547 MB fetch, resume-after-kill, the metered→Wi-Fi wait,
  and that `Qwen2.5-0.5B` actually loads and answers usefully through
  MediaPipe 0.10.27. Open sub-items: try a larger model (Qwen2.5-1.5B q8,
  ~1.6 GB) for answer quality where the device can take it; GPU backend
  instead of CPU in `LocalLlmManager`; run the download in a `WorkManager`
  job / foreground service so it survives the app being swiped away;
  `LlmInference` close/reload on `onTrimMemory`. (ABI splits and the
  `com.google.mediapipe.**` keep rules are now in place — see "APK size"
  below; R8 itself is still off pending a device check.)
- **CI pipeline — done, plus lint 2026-09-02.** `.github/workflows/ci.yml`
  runs `:domain:test :app:testDebugUnitTest` on push / PR / manual dispatch,
  and now `:app:lintDebug` with the report uploaded. Lint is **non-gating**
  for a first pass (`lint.abortOnError = false` in `app/build.gradle.kts`):
  read the report, fix or baseline what's there, then flip it to `true` so a
  regression fails CI instead of scrolling past in a log.

  Reviewed 2026-09-02 and fixed: it installed JDK 21 while
  `gradle-daemon-jvm.properties` asks for 25, so Gradle had to find or
  download a JDK mid-build; the BRouter jar was fetched from a GitHub release
  on every run with no cache (the same third-party-outage risk the Vosk model
  was already cached against); no `timeout-minutes`, no `permissions` block.

  Open:
  - No instrumented-test or `assembleRelease` job (the latter needs a signing
    config + a device to be meaningful).
  - `lint.checkDependencies` covers `:data` but **not** `:domain` — a plain
    `kotlin("jvm")` module only contributes a lint model with the
    `com.android.lint` plugin applied. Little to find there (no Android API
    surface), but it is a real gap.
  - The pinned action majors are 1–2 behind current (checkout v5 → v7,
    cache v5 → v6, upload-artifact v6 → v7, setup-gradle v5 → v6). All still
    exist and work; bump them deliberately, one at a time, where a failure
    can be read.
- **Crash reporting — finish it.** Firebase Crashlytics is now wired
  (opt-in via `app/google-services.json`, diagnostics-only, no Analytics).
  Still open: it covers JVM crashes but **not native ones** — MediaPipe,
  Vosk and MapLibre are native libs, so `firebase-crashlytics-ndk` + symbol
  upload would be needed to see those. Also worth an explicit opt-out
  toggle, and deciding whether the shipped build should carry a Firebase
  config at all vs. keeping it dev-only.

## Left open by the 2026-09-02 audit

- **Nothing outside `:domain` was compiled.** That environment had no Android
  SDK and no Maven Central. `:data` and `:app` were checked with a Kotlin
  parser and a missing-import pass only. Run the real build first.
- **Main-thread tile work.** `placeFromTiles` and `refreshPois` both run
  `querySourceFeatures` plus a geometry scan on the main thread on every
  camera idle. The arithmetic is cheap; the JNI feature query may not be, and
  it has to stay on the GL thread. Profile before restructuring.
- **The model download doesn't survive being swiped away.** It resumes from
  the `.part` next launch, which is correct but slow for a 1.5 GB file. A
  foreground service or `WorkManager` job would let it finish in the
  background. (`LocalLlmManager` now releases the engine under memory
  pressure, so the other half of that item is done.)
- **No checksum on the downloaded model** — size only. Fine against a
  truncated download, not against a corrupted one.
- **No index on `trip_exchange.at`**, so `recentExchanges` / `latestExchanges`
  full-scan and sort. The 7-day retention keeps the table tiny, so this is
  noted rather than fixed — it would cost a schema migration for no
  measurable gain today.

## Blocking real-world launch (not code — decisions/content needed)

- **Publish the privacy policy.** `PRIVACY.md` now exists (contact
  pedro13087@gmail.com); it still needs a public URL — GitHub Pages, or
  pasted into the Play Console listing — and to be kept in sync if the data
  flows ever change.
- **Play Store category for the Android Auto service** — `POI` is the best
  available fit, not a good one. Needs a conversation with Google's
  Android for Cars team before submitting for review.
