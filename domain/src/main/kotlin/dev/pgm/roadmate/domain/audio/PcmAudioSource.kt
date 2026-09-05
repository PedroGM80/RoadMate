package dev.pgm.roadmate.domain.audio

/**
 * A stream of raw 16-bit little-endian mono PCM, opened and closed by the
 * caller.
 *
 * This exists because the microphone the app records from is not always the
 * phone's. While Android Auto is projecting, the host process owns the
 * handset mic outright (and mutes it), and a `CarAppService` is a background
 * process on the phone, which Android 11+ forbids from opening the mic at
 * all — so the car screen gets silence from `AudioRecord` no matter what
 * permissions are granted. In the car the audio has to come from the host,
 * via `androidx.car.app.media.CarAudioRecord`.
 *
 * Keeping the source behind this interface lets the Vosk pipeline in :data
 * transcribe either one without knowing which it is, and keeps the Car App
 * Library out of the data layer.
 */
interface PcmAudioSource {

    /** Samples per second of the stream [read] produces. */
    val sampleRate: Int

    /** Preferred read size in bytes. */
    val bufferSize: Int

    /** Opens the stream. Throws if the mic is unavailable or not permitted. */
    fun start()

    /**
     * Fills [buffer] with up to `buffer.size` bytes.
     *
     * @return bytes read, 0 if none were available, or -1 at end of stream.
     */
    fun read(buffer: ByteArray): Int

    /** Closes the stream. Safe to call more than once. */
    fun stop()
}
