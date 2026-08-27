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
- **Handle no Maps app installed.** `MapSearchRepositoryImpl.searchNearby()`
  calls `context.startActivity()` on the `geo:` intent with no
  `ActivityNotFoundException` guard. Every device tested against has Google
  Maps, but a device without any geo-handling app installed would crash
  instead of getting a spoken "no tengo una app de mapas instalada."
- **Contact call follow-ups.** Ambiguous matches currently just say "sé más
  específico" and stop. A natural next step — without adding a full
  multi-turn dialogue system — is accepting a same-session disambiguating
  follow-up ("la de trabajo", "el segundo") instead of making the driver
  restart the whole request.

## Medium effort

- **Voice personality pass.** Flagged early as the highest-leverage lever
  for making the app feel distinctive, not yet done: a consistent tone in
  `Constants.GEMINI_SYSTEM_PROMPT` and the fixed spoken phrases (call/map
  responses, rest reminders), rather than each being written independently.
  In a voice-first app used while driving, this is what gets remembered —
  more than further visual polish.
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

- **Verify on a real Android Auto head unit or working DHU.** Still the
  single biggest unverified risk — everything car-side is only checked by
  compiling against the real `androidx.car.app` API, never actually driven.
  Blocks confidently shipping the Android Auto surface.
- **Verify AICore/Gemini Nano on real hardware.** Confirmed *absent* on the
  emulator (expected) but never confirmed *present and working* on a real
  AICore-capable device — the "IA local activa" path is unverified end to
  end.
- **CI pipeline.** No automated build/test run exists outside manually
  invoking Gradle locally. Even a minimal `./gradlew test` on push would
  catch the kind of constructor-signature breakage this session ran into
  by hand each time.
- **Privacy-respecting crash/error visibility.** Right now a crash on a
  user's device is invisible to us entirely — no reporting of any kind,
  consistent with the offline-privacy stance, but it means field issues
  (like the Maps-not-installed gap above) would only surface as bad
  reviews, not actionable reports.

## Blocking real-world launch (not code — decisions/content needed)

- **Privacy policy contact email** — still a placeholder
  (`[email de contacto pendiente de añadir]`) in the published policy.
- **Play Store category for the Android Auto service** — `POI` is the best
  available fit, not a good one. Needs a conversation with Google's
  Android for Cars team before submitting for review.
