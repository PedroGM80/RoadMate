# Testing RoadMate's Android Auto screens

There is no `@Preview` for Car App Library screens — the **host** renders the
templates, not the app, so Android Studio has nothing to draw. Two things
stand in for it.

## 1. Template tests (`CarScreensTest`)

`app/src/androidTest/.../car/CarScreensTest.kt` builds each `Screen` with fake
dependencies (`CarScreenFakes.kt`) and drives it to `STARTED`, which forces
the host stub to ask for the template. It verifies:

- `onGetTemplate()` doesn't throw — catches an `@RequiresCarApi` call the real
  host would reject at runtime, an empty `ItemList` the host refuses, a
  missing header, etc.
- the template is the type the screen is meant to show
  (`MapWithContentTemplate` for Home, `NavigationTemplate` for the bare map,
  `ListTemplate` for Settings)
- redrawing is stable
- the session opens on `HomeCarScreen`

Run them (needs a connected device or emulator — they use the real Car App
lifecycle, not Robolectric):

```
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.pgm.roadmate.car.CarScreensTest
```

On the Xiaomi (HyperOS): the first install needs **Developer options →
"Install via USB"** enabled and a prompt approved on the phone, and it can
time out — re-enable and retry if the install is "canceled by user".

These are **not** in CI — CI has no emulator. Run them in the device pass.
What they can't check is layout, colours, truncation under driving
restrictions, or that the map actually draws on the surface. That's the DHU.

## 2. Desktop Head Unit (visual)

The DHU renders the real car UI on the laptop, driven by the phone.

Installed here: `~/Library/Android/sdk/extras/google/auto/desktop-head-unit`

One-time phone setup:

1. Android Auto app → tap the version 10× to unlock **Developer settings**.
2. Developer settings → **Start head unit server**.
3. Android Auto → Settings → **"Unknown sources"** on (lets a
   `installDebug` build show up — this applies to non-templated categories,
   but keep it on for development).

Each session:

```
adb forward tcp:5277 tcp:5277
~/Library/Android/sdk/extras/google/auto/desktop-head-unit/desktop-head-unit
```

RoadMate appears in the DHU launcher once `:app:installDebug` has run. The
map screens need the `NAVIGATION` category (already set) for the host to hand
out a drawing surface.

> **A real car will not show an `adb install` build.** Android Auto's
> "Unknown sources" does not apply to Car App Library apps — in a car the app
> must come from a trusted source (Play internal testing / Internal App
> Sharing is the fastest). The DHU is the substitute for local dev.
