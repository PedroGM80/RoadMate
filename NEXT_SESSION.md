# Next session (2026-09-03)

The first on-device session happened (Xiaomi Redmi Note 14, HyperOS,
Android 16, arm64, **no AICore**). Lots fixed and committed to `develop`.
This file is now the running state, not the original bring-up plan.

## Device session 2 — 2026-09-03 (Cádiz / San Fernando, ~36.47,-6.19)

Verified on the Xiaomi:
- **Offline POI search** — the fuel chip pins 15 gas stations from the
  downloaded tiles; the filter chips are now Material Symbols icons and the
  pins carry the category icon. Fixed: stale pins of the wrong category
  stayed when switching filters zoomed-out.
- **Offline routing (BRouter)** — works. Route from the GPS fix to a fuel
  POI: engine **495 ms**, 52 pts, 1031 m, blue line follows roads, chip
  "1,0 km · 4 min". The `.rd5` tile (`W10_N35`, 46 MB) downloads from
  brouter.de over Wi-Fi with a live % in the chip; `car.brf` + `lookups.dat`
  unpack from assets. **The one bug:** `ProfileCache.parseProfile` returns
  false on a fresh parse (true = cache hit) — we were reading it as failure.
- **Wake word** — `wake: listening for "oye copiloto"` starts on launch and
  the mic-button ↔ wake-recognizer handoff works. Actual "oye copiloto"
  trigger not tested (needs a voice).
- **MapLibre attribution** lifted clear of the chip row.
- The POI-sheet button said "Ir con Google Maps" (string only — behaviour
  was already offline); now "Trazar ruta".

Still needs a voice on the device: wake-word trigger, streaming/prompt
latency, name search ("busca el Mercadona"). DebugTrace now covers the
routing path (`route:` lines).

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
   - Tighten Vosk end-of-speech — still open. Note: `RECORDING_END_SILENCE_MS`
     is a **dead constant**, nothing reads it; the real end-pointing is
     Vosk's own (baked into the model's `conf/`). Tuning needs the model
     config + device testing, not a Kotlin knob.
5. Map polish: markers slightly big / overlap when clustered; MapLibre
   attribution overlaps the chip row's corner; consider a "N cerca" count.
6. ~~Verify the OpenWeather key once it activates~~ — key is live (see
   tooling notes). Left: confirm "¿qué tiempo hace?" answers in-app on device.
7. ~~Voice search → offline map~~ — *done 2026-09-03.* "busca gasolineras"
   and friends show on RoadMate's own downloaded map; **all external-Maps
   (`geo:` / `google.navigation:`) handoffs removed** per user ("el mapa de
   Google no debe usarlo"). `PlaceCategoryParser` → `PoiKind` filter, or a
   name match against the tiles (`name` / `name:es`, accent-folded).
   `MapSearchCoordinator` bridges the voice pipeline → `RootScreen`/`
   MapViewModel`. No region downloaded → "no tengo un mapa descargado de
   esta zona", no fallback. "llévame a casa / al trabajo" routes straight to
   the saved `FactType.HOME`/`WORK` coordinate (`MapSearchRequest.
   destination`), route end marked with a dot. **Device check:** does a name
   match actually find POIs in the downloaded region? tune `NAME_PROPS` /
   the fold.
8. GPU backend for MediaPipe: dead end on this MediaTek (silent hang);
   could be made opt-in for devices where it works.
9. **Hands-free wake phrase "oye copiloto".** *Code landed 2026-09-03.*
   Foreground + background, mic button kept as fallback. **No paid parts** —
   ruled out Porcupine (Picovoice free tier is personal-use only; custom
   keywords also expire). Runs on the Vosk model already bundled for
   dictation: zero new deps, no account.
   - `WakeWordDetector` (data/ml): a Vosk `Recognizer` locked to the grammar
     `["oye copiloto", "[unk]"]` via a `SpeechService`; a partial/result
     containing "copiloto" fires. Shares the one `Model` through the new
     `VoskModelProvider`. `isAvailable()` is always true (model ships in the
     app) → hands-free replaces the silence monitor on every device.
   - `WakeWordRepository` (domain) / `…Impl` (data) — the engine stayed
     behind this seam, so the Porcupine→Vosk swap didn't touch the wiring.
   - `RoadMateViewModel`: `startWakeWordListening()` → on detection runs the
     same `startListening()` cycle; `startAmbientListening()` picks wake
     phrase **or** the rest-silence monitor (mutually exclusive — one mic
     owner). `startListening()` pauses/resumes the wake job around the Vosk
     capture, gated on a `wakeWordDesired` intent flag.
   - `WakeWordForegroundService` (data/service, `foregroundServiceType=
     microphone`): background detection → headless STT + answer, no activity
     launch. Started from `MainActivity.onPause`.

   **Known limitation:** the 30-min rest-reminder is suspended whenever
   hands-free is active (both want the mic continuously). Fix later by
   fanning one `AudioRecord` PCM stream to both the wake recognizer and the
   dB check instead of running two mic owners.

   ~~Settings toggle~~ / ~~earcon on detection~~ — *done 2026-09-03*
   ("Manos libres" switch under a "Voz" header; `Earcon.start()` on a
   hands-free hit).

   **Still TODO / device bring-up:**
   - The wake recognizer runs a full (if tiny-grammar) Kaldi decode
     continuously — add a **volume gate** (own AudioRecord + RMS, only
     `acceptWaveForm` above threshold, or `SpeechService.setPause`) to cut
     idle CPU/battery. Profile on device first; the 2-word grammar graph is
     small so it may be fine as-is.
   - Verify on device: does the small ES model spot "oye copiloto"
     reliably? Tune with a second phrasing in the grammar or the trigger
     token if not. Check for self-triggering on the assistant's own TTS
     (already guarded by the `isSpeaking` check, but confirm).
   - Background earcon: `Earcon` lives in `:app`; `WakeWordForegroundService`
     (`:data`) has no cue. Move `Earcon` to `:data` if a background blip is
     wanted.

10. **Offline routing (BRouter).** *Code landed 2026-09-03* — compiles,
    dexes, unit-tested at the seams; the **engine itself is unverified on
    device** (route quality, `.rd5` fetch, timing). No external app.
    - **Engine jar:** JitPack only publishes sources, so
      `:data:downloadBRouterJar` fetches `brouter-<v>-ro.jar` (~350 KB, MIT,
      pure Java — `btools.router/mapaccess/expressions/codec/util`) from the
      GitHub release into `data/libs/` (gitignored). Bump `brouterVersion`
      in `data/build.gradle.kts` to update.
    - `BRouterRouter` (`RoutingRepository`): unpacks bundled
      `assets/brouter/car.brf` + `lookups.dat`, `ProfileCache.parseProfile`,
      `RoutingParamCollector().getWayPointList("lon,lat|lon,lat")`,
      `RoutingEngine(null,null,segmentDir,wps,rc).doRun(25s)`, reads
      `foundTrack.nodes` → lat/lon, `.distance`, `.getTotalSeconds()`.
      Null on no-data / no-route.
    - `RoutingDataManager`: on demand, downloads the `.rd5` tile(s) a route
      needs from `brouter.de/brouter/segments4/`, **Wi-Fi-only**, resumable;
      `SegmentTiles.nameFor(lat,lon)` gives the 5° tile name.
    - `MapViewModel.routeTo` / `route` / `routeSummary`; `MapScreen` draws it
      with a `LineManager` + a chip that shows "12,3 km · 18 min", or the
      live tile-download progress / "Necesito Wi-Fi…" while it fetches.
      "llévame a X" resolves the destination to the first offline POI match,
      then routes from the current fix. R8 keep/`-dontwarn` rules for
      `btools.*` are already in `app/proguard-rules.pro`.
    - **Device bring-up:** does `RoutingEngine` run on arm64 with a real
      `.rd5` (Madrid = `W5_N40.rd5`)? Check `doRun` timing, the tile download
      over the app's network, and that the polyline renders. If the engine
      throws, log `getErrorMessage()`.
    - Still no spoken turn-by-turn (route line + distance only).

## Not for a device — still open from before

ABI splits done, CI green. R8 still off pending a verified release build.
`firebase-crashlytics-ndk` needs `google-services.json`. Privacy-policy
URL + Play Store Android Auto category are decisions, not code.
