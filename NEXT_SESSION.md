# Next session (2026-09-03)

The first on-device session happened (Xiaomi Redmi Note 14, HyperOS,
Android 16, arm64, **no AICore**). Lots fixed and committed to `develop`.
This file is now the running state, not the original bring-up plan.

## Device / tooling notes

- Xiaomi serial `8TOZG675AI4PJFHI` (USB). `adb` at
  `~/Library/Android/sdk/platform-tools`.
- HyperOS needs **Developer options → USB debugging (Security settings)** ON
  for `adb shell input` / `pm grant` to work. It's currently on but can
  time out — re-enable if taps/grants start failing.
- **MIUI suppresses logcat for third-party apps** (`log.tag=M`). The whole
  voice pipeline is traced to a file instead: `DebugTrace` →
  `/data/data/dev.pgm.roadmate/files/aicore_debug.log`. Pull with
  `adb exec-out run-as dev.pgm.roadmate cat files/aicore_debug.log`.
- `local.properties` (untracked) has: the Xiaomi doesn't matter here, but it
  now points `LOCAL_AI_MODEL_URL` at **Qwen2.5-1.5B-Instruct q8**
  (`~1.5 GB`, Apache-2.0, ungated) and has `OPENWEATHER_API_KEY` set. The key
  **activated 2026-09-02** — verified live: `data/2.5/weather` returns 200 for
  Madrid with the app's own params. Still untested end-to-end in-app.

## What works on device now

- Vosk STT (Spanish) — transcribes, though the *small* model mis-hears
  ~1 in 3 ("que este" for "quince", "le guernica", drops accents).
- TTS speaks.
- Local model: AICore absent → Qwen2.5-1.5B via MediaPipe. After the
  Qwen-0.5B→1.5B swap + prompt rework it **answers properly** (was echoing
  the question). Identity, "don't invent trip data", question-mark
  punctuation, arithmetic shortcut, weather shortcut all verified.
- Model **warm-up at startup** — first question ~4 s instead of ~11 s.
- Map: renders, street zoom, blue dot, GPS-fix poll, +/- and recenter
  buttons, "Descargar" hides when a region is saved, "Mapa offline listo"
  auto-dismisses, **POI filter pins work** (querySourceFeatures on the
  `poi` layer; icon-only circular markers).

## Open — priority order

1. **Streaming answer → TTS per sentence** — *code done 2026-09-02, needs
   on-device verification.* `LocalLlmManager.generateResponseStream`
   (`generateResponseAsync` + `ProgressListener` wrapped in `callbackFlow`,
   own inference thread); `GeminiRepository.getResponseStream` streams for
   the MediaPipe backend only (AICore/fallback still one-shot);
   `GenerateResponseUseCase.streamGeminiAnswer` speaks each sentence via the
   new `SentenceChunker` as it lands, still emits cumulative text to the UI.
   **Verify on device:** first audio latency (target ~1.5 s vs ~6 s), no
   dropped/duplicated sentences, mojibake-free speech, timeout still lands on
   "modo básico". Also folded in: `buildGeminiPrompt` fans out its 6 memory
   reads with `async` instead of running them sequentially.
2. **Strip the debug tracing** — do this LAST, right before a release build /
   R8 verification (the new streaming traces are useful for step 1's
   on-device check): `DebugTrace.kt`, all `dbg(...)` / `DebugTrace.log(...)`
   in `LocalLlmManager`,
   all `dbg(...)` / `DebugTrace.log(...)` calls in `LocalLlmManager`,
   `GeminiNanoManager`, `GeminiRepositoryImpl`, `VoskSpeechRecognizer`,
   `MapScreen.refreshPois`. Grep `DebugTrace`.
3. **Bigger Vosk model** for accuracy — `vosk-model-es-0.42` (~1.4 GB) as a
   runtime download (the small one is bundled in assets; this needs a
   download-manager path like the LLM has). Improves recognition, not
   latency.
4. Smaller latency wins: drop lat/lon + clima lines from the prompt when
   the question doesn't need them; tighten Vosk end-of-speech.
5. Map polish: markers slightly big / overlap when clustered; MapLibre
   attribution overlaps the chip row's corner; consider a "N cerca" count.
6. ~~Verify the OpenWeather key once it activates~~ — key is live (see
   tooling notes). Left: confirm "¿qué tiempo hace?" answers in-app on device.
7. Voice-search → offline-map routing (design already in git history under
   "docs: plan voice-search -> offline map routing") — now unblocked since
   the POI query works.
8. GPU backend for MediaPipe: dead end on this MediaTek (silent hang);
   could be made opt-in for devices where it works.

## Not for a device — still open from before

ABI splits done, CI green. R8 still off pending a verified release build.
`firebase-crashlytics-ndk` needs `google-services.json`. Privacy-policy
URL + Play Store Android Auto category are decisions, not code.
