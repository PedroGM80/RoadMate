# Lucide icons

The `app/src/main/res/drawable/lucide_ic_*.xml` vector drawables are Lucide
icons (<https://lucide.dev>), vendored as Android `<vector>` resources rather
than pulled as a dependency — the app needs a handful of icons and this keeps
the build lean (no ~1600-file icon artifact, no extra transitive Compose
dependency).

Source: the `com.composables:icons-lucide-android` packaging of Lucide, files
copied verbatim (stroke paths unchanged; `Icon(..., tint = ...)` recolours
the black strokes at draw time).

Currently vendored: `mic`, `map`, `square` (stop), `map_pin`, `cloud_off`,
`moon_star`, `triangle_alert`, `download`.

To add another: grab `res/drawable/lucide_ic_<name>.xml` from that artifact
(or convert the SVG from lucide.dev) and drop it in `drawable/` with the same
`lucide_ic_` prefix.

License: ISC (see `LICENSE`).
