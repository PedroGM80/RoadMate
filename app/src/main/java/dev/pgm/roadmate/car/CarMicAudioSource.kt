package dev.pgm.roadmate.car

import android.annotation.SuppressLint
import androidx.car.app.CarContext
import androidx.car.app.media.CarAudioRecord
import dev.pgm.roadmate.domain.audio.PcmAudioSource

/**
 * The car's microphone, as handed over by the Android Auto host.
 *
 * This is the only way a templated app can hear the driver. While projection
 * is running the host process holds the handset microphone exclusively and
 * mutes it, and the app's own `CarAppService` is a background process on the
 * phone, which Android 11+ bars from recording at all — so Vosk's usual
 * `AudioRecord` path returns nothing but silence in the car, whatever the
 * permission state. [CarAudioRecord] asks the host for the stream instead;
 * the host stops it on its own when the app loses the foreground or another
 * component (the assistant, a call) takes the mic.
 *
 * Needs `androidx.car.app.MICROPHONE` in the manifest — [CarAudioRecord.create]
 * throws SecurityException without it. On the Desktop Head Unit the stream is
 * whatever `mic begin` / `mic play` inject, not the laptop mic by default.
 */
class CarMicAudioSource(private val carContext: CarContext) : PcmAudioSource {

    private var record: CarAudioRecord? = null

    override val sampleRate: Int = CarAudioRecord.AUDIO_CONTENT_SAMPLING_RATE

    override val bufferSize: Int = CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE

    // CarAudioRecord.create is gated by androidx.car.app.MICROPHONE (see the
    // class KDoc), not RECORD_AUDIO, so lint's check doesn't apply here.
    @SuppressLint("MissingPermission")
    override fun start() {
        val created = CarAudioRecord.create(carContext)
        created.startRecording()
        record = created
    }

    override fun read(buffer: ByteArray): Int =
        record?.read(buffer, 0, buffer.size) ?: -1

    override fun stop() {
        record?.let { runCatching { it.stopRecording() } }
        record = null
    }
}
