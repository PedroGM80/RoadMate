# Next session — on-device bring-up (2026-09-02)

Everything code-only in the backlog is done (see `FUTURE.md`). What's left
needs a real phone. This session is the **first hardware run** of RoadMate:
get the voice loop working, then triage what breaks.

Bring: the Xiaomi (non-AICore → exercises the model-download fallback). A
Pixel 8+ / Galaxy S24+ too if you have one (AICore / Gemini Nano path).

---

## Pre-flight (~10 min)

1. `git pull` on `develop`; confirm CI is green.
2. `./gradlew :app:installDebug` — note **which ABI split** installs and its
   **size** (should be ~115–125 MB, not ~250).
3. Grant all permissions up front: mic, location (fine), contacts,
   notifications, phone.
4. Decision: drop `app/google-services.json` in now to test Crashlytics
   this session, or skip it and leave crash reporting for later.
5. `adb logcat -s RoadMate LocalAiModelManager LocalLlmManager VoskSpeechRecognizer PhoneCallRepository OfflineMapManager` in a side terminal.

---

## Phase 1 — Voice loop (do this first, it's the whole point)

- Onboarding → continue. Confirm it doesn't re-show on next launch.
- Tap mic. Check in order: earcon fires, breathing dot appears, Vosk
  **partial** text updates as you speak.
- Ask something simple ("¿qué hora es?"). Confirm: final transcription
  lands → prompt built → a spoken answer comes back.
  - Non-AICore device: expect the honest **"modo básico"** canned reply
    first. The ~547 MB model download should start on Wi-Fi — watch
    `LocalAiModelManager` in logcat.
- Error paths: revoke mic permission mid-use; trigger the no-speech
  timeout (tap mic, stay silent).

**Likely code work coming out of this:** end-pointing / silence-timeout
constants, partial-result rendering, the mic-denied message path.

## Phase 2 — Intents (no network needed, fast to run)

Run each and note real-phrasing misses:

- `Llama a <contacto>` — single match calls directly; a name shared by
  several people asks; finish with "la segunda" / a surname.
- One contact with a mobile **and** a work number → "¿el móvil o el del
  trabajo?" → answer "la del trabajo".
- `Busca gasolineras` / `llévame a <sitio>` / `¿dónde hay una farmacia?`
  → Maps opens with the query.
- `Abre Spotify` → app comes to the foreground; try one that isn't
  installed → spoken explanation.
- `Cuéntame un chiste` → local joke, no network.
- `Recuerda que no me gustan las autovías` → `¿qué sabes de mí?` reads it
  back → `olvida lo de las autovías` drops it.
- `Respuestas cortas` → confirm later answers are shorter and it sticks
  across a relaunch.

**Likely code work:** intent regexes that miss how you actually phrase
things out loud.

## Phase 3 — Map tab

- Map renders (OpenFreeMap "liberty" style, not a blank grid).
- Blue location dot appears. **If it doesn't:** MapLibre's default engine
  may need `play-services-location` wired as the location engine — that's a
  known likely fix, stage it before the session.
- `Descargar esta zona` completes → turn on airplane mode → pan around,
  map still works.
- Filter chips (gasolineras / hoteles / comida) drop pins. **Verify the
  POI layer names** — `queryRenderedFeatures(... "class")` assumes the
  OpenFreeMap schema; if no pins appear, the layer/property names are
  wrong and need fixing against the real style JSON.
- Tap a pin → Google Maps turn-by-turn launches.

## Phase 4 — Model download + inference (Wi-Fi, ~547 MB, slow)

- Fetch completes; kill the app mid-download → relaunch → it resumes.
- On mobile data it should wait; on Wi-Fi it proceeds.
- Qwen2.5-0.5B loads through MediaPipe and gives a *useful* answer, not
  word salad. If quality is poor → note it for the "better base model"
  track (try Qwen2.5-1.5B q8 via `LOCAL_AI_MODEL_URL`).

## Phase 5 — Surfaces

- QS tile: add "Preguntar a RoadMate" to Quick Settings → tap → opens
  straight into listening.
- Widget: add to home screen, resize it, tap anywhere on it → same.
- Greeting: fresh install, first open of the day → one spoken time-of-day
  greeting, not on every open.

## Phase 5b — Android Auto discovery

`automotive_app_desc.xml` was fixed (`androidx.car.app`, was `template`) so
the app can be recognised at all. To actually see it on a sideloaded build:
Android Auto → tap the version 10× to unlock **Developer settings** → enable
**Unknown sources**, then RoadMate shows in the car launcher (POI category).
If it still doesn't: check `adb logcat -s CAR.APP` while connecting.

## Phase 6 — AICore device (only if you have one)

- On a Pixel 8+ / Galaxy S24+: "IA local activa" should show **immediately**
  — no download — and answers route through Gemini Nano.

---

## After the device pass — decisions to close

- **R8:** once a clean `assembleRelease` runs on device, flip
  `app/build.gradle.kts` → `buildTypes.release.optimization.enable = true`
  and re-test the intent paths (the `-keep` set in `proguard-rules.pro` is
  already there).
- **Crashlytics:** `google-services.json` + `firebase-crashlytics-ndk` for
  native crashes; add an explicit opt-out toggle.
- **Privacy policy:** publish `PRIVACY.md` to a URL (GitHub Pages).
- **Play Store:** Android Auto service category — talk to Google's Android
  for Cars team before submitting.

## Stage before the session (so device time isn't blocked)

- [ ] `play-services-location` as the MapLibre location engine (patch ready,
      not merged — only needed if the blue dot is dead).
- [x] `google.navigation:` intent guard — already done: `launchNavigation`
      in `MapScreen.kt` wraps it in `runCatching` and falls back to `geo:`,
      itself guarded. No unit test (private Composable helper); verify by
      running it on a device with no Google Maps installed.
