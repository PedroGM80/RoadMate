# Wake-word model files (per-machine, not committed)

`WakeWordDetector` loads the Picovoice Porcupine model from here. Both files
are gitignored — drop your own copies in:

- `roadmate.ppn` — the trained "RoadMate" keyword, built at
  <https://console.picovoice.ai> (Porcupine → create wake word). Pick the
  **Android** platform. Choose the language you'll say it in (the Spanish
  model tends to match a Spanish-accented "RoadMate" better).
- `porcupine_params.pv` — the language params that match the `.ppn`. Download
  the one for the same language from
  <https://github.com/Picovoice/porcupine/tree/master/lib/common>
  (`porcupine_params_es.pv` for Spanish) and rename it to
  `porcupine_params.pv`.

Also set `PICOVOICE_ACCESS_KEY` in the repo-root `local.properties`.

Without all three (key + both files) the app just uses the mic button.

**Licensing:** on the Picovoice free plan a custom `.ppn` is time-limited and
must be regenerated periodically. Fine for development; a shipped build needs
a paid Picovoice plan (or the Vosk restricted-grammar fallback).
