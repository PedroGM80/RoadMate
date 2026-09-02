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
  (Aside: warm-up prompt "Di \"listo\"." makes the model ramble ~40 English
  words, ~3 s wasted — harmless, background, but could be a 1-token dummy.)
- **Streaming answer → TTS** (verified 2026-09-02, 3 rounds): speaks each
  sentence as it is generated. 3-sentence reply → first audio 2.8 s before
  generation finished; no dropped / duplicated / overlapping sentences.
  First-audio latency itself is **~4.5–4.9 s** (prompt prefill + ~6 tok/s on
  this CPU), so the ~1.5 s target needs the shorter prompt in item 4.
  Mojibake in speech fixed — the corruption is *partial* (mixed intact and
  double-encoded accents in one reply), so `fixMojibake` now rewrites only
  the `Ã`/`Â`+continuation pattern instead of round-tripping the whole string.
- Map: renders, street zoom, blue dot, GPS-fix poll, +/- and recenter
  buttons, "Descargar" hides when a region is saved, "Mapa offline listo"
  auto-dismisses, **POI filter pins work** (querySourceFeatures on the
  `poi` layer; icon-only circular markers).

## Open — priority order

1. ~~**Streaming answer → TTS per sentence**~~ — *done + verified on device
   2026-09-02.* `LocalLlmManager.generateResponseStream` (`generateResponseAsync`
   + `ProgressListener` in `callbackFlow`, own inference thread, guaranteed
   final `send()` before `close()`); `GeminiRepository.getResponseStream`
   streams for the MediaPipe backend only; `GenerateResponseUseCase`
   `.streamGeminiAnswer` speaks each `SentenceChunker` sentence as it lands
   and still emits cumulative text to the UI. `buildGeminiPrompt` also fans
   its 6 memory reads out with `async`. Remaining latency work is item 4.
2. **Strip the debug tracing** — do this LAST, right before a release build /
   R8 verification: `DebugTrace.kt`, all `dbg(...)` / `DebugTrace.log(...)`
   calls in `LocalLlmManager` (incl. the `stream …` lines), `GeminiNanoManager`,
   `GeminiRepositoryImpl`, `VoskSpeechRecognizer`, `WakeWordDetector` (the
   `wake: …` lines), `MapScreen.refreshPois`, and the **TEMP
   `DebugTrace.log("TTS speak …")` in `TextToSpeechManager`** (added for the
   streaming-latency check). Grep `DebugTrace`.
3. ~~**Bigger Vosk model** for accuracy — `vosk-model-es-0.42` (~1.4 GB)~~ —
   **dropped.** Tried before; it recognised *worse* than the small one on
   this device (per user, 2026-09-03). Not worth the ~1.4 GB runtime
   download. Accuracy has to come from elsewhere — restricted grammar for
   command phrases, or the wake-word engine in item 9.
4. Smaller latency wins.
   - ~~Drop lat/lon + clima lines from the prompt when the question doesn't
     need them~~ — *done 2026-09-03* (`PromptBuilder`
     `LOCATION_QUESTION` / `WEATHER_QUESTION` keyword gates; coord + Clima
     lines emitted only for spatial / weather questions, bias to include).
     Device measurement of the first-audio latency still pending.
   - Tighten Vosk end-of-speech (`RECORDING_END_SILENCE_MS = 5_000L` is
     long) — still open.
5. Map polish: markers slightly big / overlap when clustered; MapLibre
   attribution overlaps the chip row's corner; consider a "N cerca" count.
6. ~~Verify the OpenWeather key once it activates~~ — key is live (see
   tooling notes). Left: confirm "¿qué tiempo hace?" answers in-app on device.
7. Voice-search → offline-map routing (design already in git history under
   "docs: plan voice-search -> offline map routing") — now unblocked since
   the POI query works.
8. GPU backend for MediaPipe: dead end on this MediaTek (silent hang);
   could be made opt-in for devices where it works.
9. **Hands-free wake word "RoadMate".** *Code landed 2026-09-03* (user
   picked Picovoice Porcupine). Foreground + background, mic button kept as
   fallback:
   - `WakeWordDetector` (data/ml) wraps `PorcupineManager` in a callbackFlow;
     `WakeWordRepository` (domain) / `…Impl` (data). No-ops unless a key +
     both model files are present.
   - `RoadMateViewModel`: `startWakeWordListening()` → on detection runs the
     same `startListening()` cycle; `startAmbientListening()` picks wake word
     **or** the rest-silence monitor (mutually exclusive — one mic owner).
     `startListening()` pauses/resumes the wake job around the Vosk capture.
   - `WakeWordForegroundService` (data/service, `foregroundServiceType=
     microphone`): background detection → headless STT + answer. Started from
     `MainActivity.onPause` when configured.
   - `libs.porcupine.android` = `ai.picovoice:porcupine-android:4.0.2`;
     `PICOVOICE_ACCESS_KEY` from `local.properties` → `BuildConfig`.

   **To actually turn it on (device prereqs, not code):**
   1. Free AccessKey from console.picovoice.ai → `local.properties`:
      `PICOVOICE_ACCESS_KEY=...`
   2. Train a "RoadMate" wake word (console → Porcupine, platform **Android**,
      pick the language) → save as `data/src/main/assets/wake/roadmate.ppn`.
   3. Grab the matching `porcupine_params_<lang>.pv` from the porcupine repo
      (`lib/common/`), rename to
      `data/src/main/assets/wake/porcupine_params.pv`.
   (`.ppn`/`.pv` are gitignored; see `assets/wake/README.md`.)

   **Licensing:** free-plan custom `.ppn` is time-limited and needs
   periodic regeneration — fine for dev, a shipped build needs a paid
   Picovoice plan or the Vosk restricted-grammar fallback.

   **Known limitation:** the 30-min rest-reminder is suspended whenever
   hands-free is active (both want the mic continuously). Fix later by
   fanning one `AudioRecord` PCM stream to both Porcupine and the dB check
   instead of running two mic owners.

   **Still TODO:** settings toggle to disable hands-free; earcon on
   detection; device bring-up (needs the prereqs above).

## Not for a device — still open from before

ABI splits done, CI green. R8 still off pending a verified release build.
`firebase-crashlytics-ndk` needs `google-services.json`. Privacy-policy
URL + Play Store Android Auto category are decisions, not code.
