# Future improvements

Ideas and known gaps that came up while building RoadMate but aren't done.
Roughly ordered easiest/highest-impact first within each section — not a
commitment, just a starting point for the next session. See
[`PROGRESS.md`](PROGRESS.md) for what's already shipped and verified.

## Quick wins (small effort, real impact)

- **Wider `MapSearchIntentParser` coverage.** Now handles navigation
  phrasings ("llévame a…", "cómo llego a…", "guíame hasta…") separately from
  the "busca/encuentra/dónde hay/dónde está/hay alguna" find family, strips
  more proximity filler ("por aquí", "en la zona", bare "cercana"), and the
  fact-lookup guard covers "quién/cuánto/cuándo/qué significa". Still
  hand-written regex — revisit against real spoken phrasing on a device.
- **Voice search → in-app offline map for known categories.** "busca
  gasolineras" currently always fires a `geo:` intent (external Maps). For
  the categories the offline map can pin (fuel / hotel / food) it should
  switch to the Mapa tab and apply that filter instead, falling back to
  `geo:` for everything else or when no region is downloaded. Blocked on
  verifying the offline POI layer query on hardware first — full design in
  `NEXT_SESSION.md`.
- **Contact follow-up: label-aware — done.** `ContactMatch` now carries a
  `PhoneLabel` (mobile / work / home / main / other) read from
  `Phone.TYPE`. One contact with two labelled numbers is treated as
  ambiguous ("Ana tiene varios números: el móvil o el del trabajo. ¿Cuál?")
  and `CallFollowUpParser` resolves "el móvil" / "la del trabajo" / "la de
  casa". Open: custom labels (`Phone.LABEL` free text) aren't matched, only
  the standard types.

## Medium effort

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
    recognition of exactly those.
  - *Feedback → few-shot.* Let the driver react ("más corto", "no era eso");
    store corrections locally and prepend a couple as guidance.
  - *Better base model (build-time).* `LOCAL_AI_MODEL_URL` is overridable —
    LoRA-finetune Qwen2.5-0.5B on a driving-assistant set offline, merge,
    convert to `.task`, ship that URL.
- **Home screen widget — done.** `RoadMateWidgetProvider` (plain
  `AppWidgetProvider`, RemoteViews, no Glance dependency) puts the same
  one-tap-to-listen action on the home screen: brand-blue pill, mic icon +
  "Preguntar", resizable, `updatePeriodMillis=0` (nothing to refresh). Taps
  fire `MainActivity` with `EXTRA_START_LISTENING`, same path as the tile.
  Not yet checked on a device.
- **Accessibility audit beyond the current pass.** TalkBack live regions
  and merged semantics exist on the core screens, and the design pass added
  reduce-motion handling + 48dp targets, but there's been no dedicated
  Switch Access or measured contrast-ratio pass, no font-scale check, and
  Android Auto's own accessibility surface hasn't been touched.
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
- **CI pipeline — done.** `.github/workflows/ci.yml` runs
  `:domain:test :app:testDebugUnitTest` on push / PR / manual dispatch
  (JDK 21 Temurin, `gradle/actions/setup-gradle`, Vosk model cached).
  Open: no instrumented-test or `assembleRelease` job yet (the latter needs
  signing config + a device to be meaningful).
- **Crash reporting — finish it.** Firebase Crashlytics is now wired
  (opt-in via `app/google-services.json`, diagnostics-only, no Analytics).
  Still open: it covers JVM crashes but **not native ones** — MediaPipe,
  Vosk and MapLibre are native libs, so `firebase-crashlytics-ndk` + symbol
  upload would be needed to see those. Also worth an explicit opt-out
  toggle, and deciding whether the shipped build should carry a Firebase
  config at all vs. keeping it dev-only.

## Blocking real-world launch (not code — decisions/content needed)

- **Publish the privacy policy.** `PRIVACY.md` now exists (contact
  pedro13087@gmail.com); it still needs a public URL — GitHub Pages, or
  pasted into the Play Console listing — and to be kept in sync if the data
  flows ever change.
- **Play Store category for the Android Auto service** — `POI` is the best
  available fit, not a good one. Needs a conversation with Google's
  Android for Cars team before submitting for review.
