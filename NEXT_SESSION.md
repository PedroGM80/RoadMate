# Next session

Running state. Two things happened since the last device session: the
2026-09-03 bring-up on the Xiaomi (below), and a full audit + hardening pass
on 2026-09-02 (`fix/audit-2026-09-02`, see `PROGRESS.md` for the findings).

## Read this first

**Nothing from the audit pass has been compiled beyond `:domain`, and nothing
has run on a device.** That environment had no Android SDK and no Maven
Central, so:

- `:domain` was genuinely compiled and its tests genuinely run — **114
  passing**, including three that were *already failing on `develop`*.
- `:data` and `:app` were checked with a Kotlin parser (syntax) and a
  missing-import pass, which is not the same as a compile.

So the first two things to do are:

```
./gradlew :domain:test :app:testDebugUnitTest
./gradlew :app:lintDebug          # new, non-gating for now — read the report
./gradlew :app:installDebug
```

**Your CI was red before any of this** — those three `:domain` failures. Worth
confirming that's now green.

The workflow itself was also reviewed and fixed on 2026-09-02. It was valid
and every action version it pins genuinely exists (checked against the real
tag lists), but: it installed JDK 21 while `gradle-daemon-jvm.properties` asks
the daemon for 25, so Gradle had to locate or download a JDK partway through
every build; the BRouter jar was refetched from a GitHub release each run with
no cache; and there was no `timeout-minutes` or `permissions` block. All four
fixed. The pinned action majors are still 1–2 behind current — deliberately
left alone, since bumping a major blind is how you get a red build for a
reason unrelated to the code.

## Open — priority order

1. **Merge check.** Build, run both test tasks, install on the Xiaomi, and
   walk the golden path once: wake word → question → spoken answer, a call, a
   map search, a route. The audit touched the mic handoff, the TTS engine, the
   answer cache and the map's lifecycle; those are what to watch.

2. **Confirm the audit's behaviour fixes on device.** Each of these was found
   by reading, not by running:
   - One "oye copiloto" should now trigger *once*, not repeatedly.
   - The mic handoff from wake word to Vosk should no longer race (watch for
     "could not open microphone" in `DebugTrace`).
   - "¿qué hora es?" and friends should now be recognised as questions (this
     is the `(?U)` regex fix — the single biggest behavioural change).
   - "pon música", "tengo hambre", "con más detalle", "ponme con Ana".
   - Tap the mic mid-answer: it should stop talking.

3. **Strip the debug tracing** — do this LAST, right before a release build /
   R8 verification: `DebugTrace.kt`, all `dbg(...)` / `DebugTrace.log(...)`
   calls in `LocalLlmManager`, `GeminiNanoManager`, `GeminiRepositoryImpl`,
   `VoskSpeechRecognizer`, `WakeWordDetector`, `MapScreen.refreshPois`,
   `BRouterRouter`, `WeatherDataSource`, and the TEMP `"TTS speak …"` in
   `TextToSpeechManager`. Grep `DebugTrace`. (It now writes on a background
   thread and caps the file at 4 MB, so it is no longer a latency or disk
   risk in the meantime.)

4. **Latency, remaining.** Streaming answer → TTS is done and verified
   (2026-09-02): first audio 2.8 s before generation finished, 3 rounds, no
   dropped or duplicated sentences. First-audio latency itself is ~4.5–4.9 s.
   Since then, two things should have helped and want re-measuring:
   - the weather lookup is no longer on the critical path of every answer
     (4 s cap + a 10-minute cache, was up to 30 s of OkHttp defaults);
   - `PromptBuilder` already gates the coord/clima lines on the question.

   Still open: tighten Vosk end-of-speech. Note `RECORDING_END_SILENCE_MS` is
   a dead constant, nothing reads it — the real end-pointing is Vosk's own,
   baked into the model's `conf/`. Tuning needs the model config and a device,
   not a Kotlin knob.

5. **Vosk accuracy.** Still mis-hears roughly 1 in 3. The bigger model
   (`vosk-model-es-0.42`, ~1.4 GB) was tried and was *worse* on this device.
   Accuracy has to come from elsewhere. Note that some of what looked like
   mis-recognition may actually have been the `\b` bug — the transcript was
   right and the *parser* didn't match it. Re-assess before investing here.

6. **Wake word on device, remaining.**
   - Add a volume gate (own `AudioRecord` + RMS, or `SpeechService.setPause`)
     to cut idle CPU/battery — the recognizer runs a full, if tiny-grammar,
     Kaldi decode continuously. Profile first; the 2-word graph may be fine.
   - Confirm it doesn't self-trigger on RoadMate's own TTS (guarded by the
     `isSpeaking` check, but unconfirmed).
   - `Earcon` lives in `:app`, so `WakeWordForegroundService` (`:data`) has no
     audible cue in the background. Move it to `:data` if that's wanted.
   - **Known limitation:** the 30-min rest reminder is suspended whenever
     hands-free is active — both want the mic continuously. The fix is to fan
     one `AudioRecord` PCM stream to both the wake recognizer and the dB
     check instead of running two mic owners.

7. **Foreground-service starts.** `MainActivity.onPause()` starts a
   microphone FGS. On Android 14+ that can be refused outright depending on
   what caused the pause. The call no longer crashes when it is (it's caught
   and logged), but **confirm background listening actually survives** the
   screen locking and an incoming call — losing it silently is the failure
   mode to look for.

8. **Map polish.** Markers slightly big / overlap when clustered; consider a
   "N cerca" count. Also profile `placeFromTiles` / `refreshPois`: both run
   `querySourceFeatures` plus a geometry scan on the main thread on every
   camera idle. Probably fine, never measured.

9. **R8.** Still off pending a verified release build.
   `app/proguard-rules.pro` already carries the keep set.

10. **Offline routing (BRouter).** Verified working on device 2026-09-03
    (engine 495 ms, 52 pts, 1031 m, tile download over Wi-Fi with live %).
    Still no spoken turn-by-turn — route line + distance only.

## Device / tooling notes

- Xiaomi serial `8TOZG675AI4PJFHI` (USB). `adb` at
  `~/Library/Android/sdk/platform-tools`.
- HyperOS needs **Developer options → USB debugging (Security settings)** ON
  for `adb shell input` / `pm grant` to work. It can time out — re-enable if
  taps/grants start failing.
- **MIUI suppresses logcat for third-party apps** (`log.tag=M`). The voice
  pipeline traces to a file instead:
  `/data/data/dev.pgm.roadmate/files/aicore_debug.log`. Pull with
  `adb exec-out run-as dev.pgm.roadmate cat files/aicore_debug.log`.
- `local.properties` (untracked) points `LOCAL_AI_MODEL_URL` at
  **Qwen2.5-1.5B-Instruct q8** (~1.5 GB, Apache-2.0, ungated) and has
  `OPENWEATHER_API_KEY` set (activated 2026-09-02, verified live against
  `data/2.5/weather` for Madrid; still untested end-to-end in-app).

## Device session 2 — 2026-09-03 (Cádiz / San Fernando, ~36.47,-6.19)

Verified on the Xiaomi:

- **Offline POI search** — the fuel chip pins 15 gas stations from the
  downloaded tiles; filter chips are Material Symbols icons and pins carry the
  category icon. Fixed: stale pins of the wrong category stayed when switching
  filters zoomed-out.
- **Offline routing (BRouter)** — works, timings above. The one bug was
  `ProfileCache.parseProfile` returning false on a fresh parse (true = cache
  hit); we were reading it as failure.
- **Wake word** — starts on launch, mic-button ↔ wake-recognizer handoff
  works. Actual "oye copiloto" trigger not tested (needs a voice).
- **Streaming answer → TTS** — verified, 3 rounds.
- **Model warm-up at startup** — first question ~4 s instead of ~11 s.
- Vosk STT transcribes, though the small model mis-hears ~1 in 3.
- Map renders, street zoom, blue dot, GPS-fix poll, controls, offline region.

## Not for a device — still open from before

ABI splits done. `firebase-crashlytics-ndk` needs `google-services.json` and
would be what catches MediaPipe/Vosk/MapLibre *native* crashes, which the
current JVM-only Crashlytics does not. Privacy-policy URL and the Play Store
Android Auto category are decisions, not code.
