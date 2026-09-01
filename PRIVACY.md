# RoadMate — Privacy Policy

_Last updated: 2 September 2026_

RoadMate is a voice copilot for driving. It is built to work **on your
device**: your voice and your questions are captured, transcribed, and
answered locally, and are never sent anywhere.

This policy describes exactly what data RoadMate touches, what stays on the
device, and the few cases where something leaves it.

## The short version

- **Your voice is never recorded to a file and never leaves the phone.** It
  is transcribed on-device (Vosk) and discarded.
- **Your questions and the assistant's answers never leave the phone.** They
  are generated on-device (Gemini Nano via AICore where available, otherwise
  a small open model run locally through MediaPipe).
- RoadMate has **no account, no login, no analytics, no advertising, and no
  tracking**.
- Some builds enable **crash reporting** (Firebase Crashlytics). When it's
  on, it uploads a diagnostic report *only if the app crashes* — stack
  trace, device model and OS version, and app state at the moment of the
  crash. It **never** sends your voice, your questions, the answers, your
  location, or your contacts. Builds without a Firebase config file have no
  crash reporting at all.
- Beyond crash reports, the app makes network requests for only **two**
  things, both described below: an optional weather lookup, and a one-time
  download of the local-AI model file.

## What stays on your device

| Data | Why | Where it goes |
|---|---|---|
| Microphone audio | Speech-to-text while you are asking something | Processed in memory on-device by Vosk, then discarded. Not written to storage, not transmitted. |
| Your question / the spoken answer | To answer you | Processed on-device (AICore / a locally-run model). Recent answers from the current trip are kept in memory for continuity and cleared when the app stops. |
| Approximate & precise location | Trip context for answers, centring the map, and biasing a "find a petrol station" search | Stays on-device, **except** it is sent to the weather provider when the weather feature is enabled (see below). |
| Contacts | To turn "call Ana" into a phone number | Read locally at the moment you ask. Not uploaded, not stored by RoadMate. |
| Onboarding state and the date of the last daily greeting | So the intro screen and greeting show once | Stored locally (Android DataStore). Never transmitted. |

## When data leaves your device

### 0. Crash reports (only in a Firebase-configured build, only after a crash)

If the build has Firebase Crashlytics enabled (it needs a
`google-services.json` config file that is not part of the source tree), a
crash report is sent to Google's Firebase servers the next time the app is
launched with a connection. The report contains: the stack trace, the
device model and Android version, and app/thread state at the time of the
crash, plus a random per-install identifier Crashlytics uses to group
reports. It does **not** contain your voice, transcripts, answers,
location, contacts, or any prompt content. See Firebase's own privacy and
data-handling terms for how Google processes it. A build without the config
file does not include Crashlytics at all.

### 1. Weather (optional)

If the build you are running has weather enabled, RoadMate sends your
**approximate coordinates** to OpenWeather (`api.openweathermap.org`) to get
the current conditions for your prompt context. No other data is included.
If weather is not configured, this request is never made. See OpenWeather's
own privacy policy for how they handle requests.

### 2. Local-AI model download (one time, Wi-Fi only)

On a device without built-in on-device AI (AICore), RoadMate downloads a
small, openly-licensed language model file once, over HTTPS, from Hugging
Face's public content CDN (`huggingface.co`). This is a plain download of a
static file: it carries **no account, no identifier, and none of your query
data**. As with any HTTPS request, the host can see standard connection
metadata (your IP address, the file requested, timestamp). The download runs
only on an unmetered (Wi-Fi) connection and only once.

## Requests handed to other apps you already have

When you ask RoadMate to do something another app owns, it hands that app an
Android intent and nothing more:

- **"Find …"** → a `geo:` search string is passed to your installed maps app.
- **"Call …"** → a phone number is passed to the dialer.
- **"Open Spotify / YouTube Music"** → a launch request is passed to that app.
- **Map navigation** → a `google.navigation:` intent is passed to Google Maps.

RoadMate does not query any places, directory, or maps API itself.

## Text-to-speech

Answers are spoken using Android's built-in `TextToSpeech`. Which engine
handles that, and whether it runs fully on-device, depends on the
text-to-speech engine configured in your system settings.

## Permissions and why they are requested

- **Microphone** (`RECORD_AUDIO`) — to hear your questions, and, in the
  background, to detect long silences for rest reminders.
- **Location** (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) — trip
  context, the map, and location-biased searches.
- **Internet / network state** (`INTERNET`, `ACCESS_NETWORK_STATE`) — the
  two requests above, and to check for Wi-Fi before the model download.
- **Foreground service / microphone foreground service**
  (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`) — to keep the
  rest-reminder silence monitor running while the app is in the background.
- **Notifications** (`POST_NOTIFICATIONS`) — the ongoing notification that
  the background monitor is required to show.
- **Phone** (`CALL_PHONE`) — to place a call when you ask.
- **Contacts** (`READ_CONTACTS`) — to resolve a name to a number.

## Children

RoadMate is not directed at children and collects no data from anyone.

## Changes to this policy

If this policy changes, the "last updated" date above will change and the
new version will be published in this repository.

## Contact

Questions about privacy: **pedro13087@gmail.com**
