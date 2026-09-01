package dev.pgm.roadmate.domain.model

/**
 * The single source of truth for what the UI should say about on-device AI.
 * RoadMate has two local backends: AICore / Gemini Nano (present on a short
 * allow-list of devices) and, as a universal fallback, a small model
 * downloaded at runtime and run through MediaPipe. This models every resting
 * and transitional state across both, plus the "not possible here" case, so
 * `HomeScreen` never has to reconstruct it from loose booleans.
 */
sealed interface LocalAiStatus {

    /** Still probing AICore / checking for the model file — initial state. */
    data object Checking : LocalAiStatus

    /** AICore / Gemini Nano works on this hardware; nothing to download. */
    data object ReadyAicore : LocalAiStatus

    /** No AICore, but the downloaded model is on disk and usable. */
    data object ReadyLocalModel : LocalAiStatus

    /** No AICore and the model file isn't downloaded yet — a fetch will start. */
    data object ModelDownloadable : LocalAiStatus

    /** The model file is downloading. [progress] is 0f‑1f, best effort. */
    data class Downloading(val progress: Float) : LocalAiStatus

    /** Download is held until an unmetered (Wi-Fi) network is available. */
    data object WaitingForWifi : LocalAiStatus

    /** The model download failed; [message] is a short human-readable reason. */
    data class DownloadFailed(val message: String) : LocalAiStatus

    /**
     * No AICore and the download path is disabled (blank model URL) —
     * "modo básico" with no way out on this build.
     */
    data object Unavailable : LocalAiStatus
}
