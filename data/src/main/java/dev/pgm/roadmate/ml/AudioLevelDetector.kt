package dev.pgm.roadmate.ml

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import dev.pgm.roadmate.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Monitors the microphone input level in real time and reports sustained silence.
 *
 * All audio reading happens on [Dispatchers.IO] via coroutines (AudioRecord.read is a
 * blocking hardware call), so it never blocks the UI thread. Callbacks ([onLevelChanged],
 * [onSilenceDetected]) are invoked from that background context — hop back to the main
 * dispatcher in the caller if touching UI state directly.
 *
 * Not a shared/injected singleton: callers construct a fresh instance per use with the
 * threshold/duration that fits their purpose (e.g. a short gap to end a recording vs a
 * long one to suggest a rest break).
 */
class AudioLevelDetector(
    private val silenceThresholdDb: Double = Constants.SILENCE_THRESHOLD_DB,
    private val silenceDurationMs: Long = Constants.REST_REMINDER_SILENCE_MS,
    private val onLevelChanged: (dB: Double) -> Unit = {},
    private val onSilenceDetected: (duration: Long) -> Unit = {}
) {

    @Volatile
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var silenceStartedAtMs: Long? = null
    private var silenceAlreadyReported = false

    val isRunning: Boolean
        get() = recordingJob?.isActive == true

    /**
     * Starts monitoring. Returns false if the microphone could not be opened —
     * no permission, no mic, or another owner holds it — so the caller can say
     * so instead of sitting on a monitor that will never fire. The
     * `AudioRecord` constructor throws (SecurityException without
     * RECORD_AUDIO), and an uncaught throw here used to take down whatever
     * collected the silence flow.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(): Boolean {
        if (isRunning) return true

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) return false

        val bufferSize = minBufferSize * 2
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        }.getOrNull() ?: return false

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        audioRecord = record
        silenceStartedAtMs = null
        silenceAlreadyReported = false

        record.startRecording()

        // The record is stopped and released *on this thread*, in the reading
        // loop's own finally. stop() used to release it from the caller's
        // thread while read() was still blocked inside the native call, which
        // is undefined behaviour and can take the process down.
        recordingJob = scope.launch {
            val buffer = ShortArray(bufferSize / 2)
            try {
                while (isActive) {
                    val samplesRead = record.read(buffer, 0, buffer.size)
                    if (samplesRead > 0) {
                        val db = amplitudeToDb(buffer, samplesRead)
                        onLevelChanged(db)
                        checkForSilence(db)
                    } else if (samplesRead < 0) {
                        break // ERROR_INVALID_OPERATION / ERROR_DEAD_OBJECT
                    }
                }
            } finally {
                releaseRecord(record)
            }
        }
        return true
    }

    fun stop() {
        val job = recordingJob
        recordingJob = null
        if (job != null) {
            // Unblocking read() so the loop's finally can run promptly; the
            // release itself still happens there, never here.
            runCatching {
                audioRecord?.takeIf { it.recordingState == AudioRecord.RECORDSTATE_RECORDING }?.stop()
            }
            job.cancel()
        } else {
            audioRecord?.let(::releaseRecord)
        }

        silenceStartedAtMs = null
        silenceAlreadyReported = false
    }

    private fun releaseRecord(record: AudioRecord) {
        runCatching {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
        }
        runCatching { record.release() }
        if (audioRecord === record) audioRecord = null
    }

    private fun amplitudeToDb(buffer: ShortArray, samplesRead: Int): Double {
        var sumOfSquares = 0.0
        for (i in 0 until samplesRead) {
            val sample = buffer[i].toDouble()
            sumOfSquares += sample * sample
        }
        val rms = sqrt(sumOfSquares / samplesRead)
        if (rms < 1.0) return SILENCE_FLOOR_DB
        return 20 * log10(rms / SHORT_MAX_AMPLITUDE)
    }

    private fun checkForSilence(currentDb: Double) {
        val now = System.currentTimeMillis()

        if (currentDb < silenceThresholdDb) {
            val startedAt = silenceStartedAtMs ?: now.also { silenceStartedAtMs = it }
            val elapsed = now - startedAt
            if (!silenceAlreadyReported && elapsed >= silenceDurationMs) {
                silenceAlreadyReported = true
                onSilenceDetected(elapsed)
            }
        } else {
            silenceStartedAtMs = null
            silenceAlreadyReported = false
        }
    }

    private companion object {
        /**
         * 16 kHz, not CD rate. This monitor holds the microphone for the whole
         * trip, and all it ever computes is one RMS figure per buffer — a
         * level, not speech. 44.1 kHz meant reading and squaring 2.75x more
         * samples per second for a number that does not change, on a device
         * that is running navigation off a car charger at best. It also lines
         * up with what Vosk and the wake word already use, so the mic doesn't
         * have to be reconfigured between owners. dBFS is a ratio, so the
         * silence threshold needs no recalibration.
         */
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val SHORT_MAX_AMPLITUDE = 32_767.0
        const val SILENCE_FLOOR_DB = -120.0
    }
}
