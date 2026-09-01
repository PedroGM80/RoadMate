# Future improvements

Ideas and known gaps that came up while building RoadMate but aren't done.
Roughly ordered easiest/highest-impact first within each section — not a
commitment, just a starting point for the next session. See
[`PROGRESS.md`](PROGRESS.md) for what's already shipped and verified.

## Quick wins (small effort, real impact)

- **Custom branded earcon.** The mic start/stop sound currently uses
  `ToneGenerator`'s built-in `TONE_PROP_BEEP`/`BEEP2` — free, no asset, but
  generic. Swapping in a short custom two-note sound (via `SoundPool`) would
  read as more "designed" for not much more work.
- **Wider `MapSearchIntentParser` coverage.** It's a simple regex on
  "busca/encuentra X" and "dónde hay X", untested against varied real
  phrasing. Likely false positive: "busca información sobre..." would
  currently also get sent to Maps. Worth a pass once real usage surfaces
  actual phrasing patterns, rather than guessing more regexes upfront.
- **Contact call follow-ups.** Ambiguous matches currently just say "sé más
  específico" and stop. A natural next step — without adding a full
  multi-turn dialogue system — is accepting a same-session disambiguating
  follow-up ("la de trabajo", "el segundo") instead of making the driver
  restart the whole request.

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
  Shipped so far: `RoadMateDatabase` (Room), conversation-history continuity,
  `PREFERENCE` facts ("recuerda que…"), and `PLACE` facts from map searches —
  all folded into the prompt. Still open:
  - *HOME / WORK / RELATIONSHIP facts.* "esta es mi casa" (store current
    location), "el trabajo es aquí", "X es mi hermano" → then "llama a mi
    hermano" resolves through memory before hitting contacts by literal name.
  - *FTS recall.* Room FTS4/5 over `trip_exchange` + `user_fact` so "¿qué te
    dije sobre el hotel de Ronda?" works. No embeddings/vector DB needed at
    this data scale — a phrase match covers it.
  - *Capture from the map's own nav button*, not just voice "busca X".
    Normalise place strings instead of storing raw queries.
  - *Instrumented DAO test* — the Room layer is only exercised via fakes.
  - *STT vocabulary.* Vosk takes a phrase-list grammar at `Recognizer(...)`;
    feeding it contact names + frequent place names sharply improves
    recognition of exactly those.
  - *Feedback → few-shot.* Let the driver react ("más corto", "no era eso");
    store corrections locally and prepend a couple as guidance.
  - *Better base model (build-time).* `LOCAL_AI_MODEL_URL` is overridable —
    LoRA-finetune Qwen2.5-0.5B on a driving-assistant set offline, merge,
    convert to `.task`, ship that URL.
- **Quick Settings Tile / home screen widget.** A one-tap "ask RoadMate"
  entry point without opening the app first. Real effort (new Android
  surface, its own lifecycle), but a plausible retention lever.
- **Multi-language support.** Everything — prompts, fixed phrases, jokes,
  UI copy — is Spanish-only right now. Would need `PromptBuilder`,
  `Constants`, `JokeProvider`, `CallIntentParser`/`MapSearchIntentParser`'s
  regexes, and every Compose string reworked for locale awareness.
- **Accessibility audit beyond the current pass.** TalkBack live regions
  and merged semantics exist on the core screens, but there's been no
  dedicated Switch Access or contrast-ratio pass, and Android Auto's own
  accessibility surface hasn't been touched at all.

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

- **APK size / ABI splits.** The all-ABI debug APK is now ~250 MB (MediaPipe
  + Vosk + MapLibre native libs + the 39 MB Vosk model). A release build
  needs per-ABI splits / an app bundle; consider moving the Vosk model to a
  runtime download and swapping `vosk-model-small` for something smaller to
  shrink the base install.

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
  `LlmInference` close/reload on `onTrimMemory`; R8 keep rules for
  `com.google.mediapipe.**` once release minification is on; ABI splits to
  trim the ~50–100 MB of `tasks-genai` native libs from the base APK.
- **CI pipeline.** No automated build/test run exists outside manually
  invoking Gradle locally. Even a minimal `./gradlew test` on push would
  catch the kind of constructor-signature breakage this session ran into
  by hand each time.
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
