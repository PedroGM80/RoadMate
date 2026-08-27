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

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var silenceStartedAtMs: Long? = null
    private var silenceAlreadyReported = false

    val isRunning: Boolean
        get() = recordingJob?.isActive == true

    /** Milliseconds of continuous silence so far, or 0 if not currently silent. */
    fun getSilenceDuration(): Long {
        val startedAt = silenceStartedAtMs ?: return 0L
        return System.currentTimeMillis() - startedAt
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (isRunning) return

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) return

        val bufferSize = minBufferSize * 2
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }

        audioRecord = record
        silenceStartedAtMs = null
        silenceAlreadyReported = false

        record.startRecording()

        recordingJob = scope.launch {
            val buffer = ShortArray(bufferSize / 2)
            while (isActive) {
                val samplesRead = record.read(buffer, 0, buffer.size)
                if (samplesRead > 0) {
                    val db = amplitudeToDb(buffer, samplesRead)
                    onLevelChanged(db)
                    checkForSilence(db)
                }
            }
        }
    }

    fun stop() {
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.let { record ->
            runCatching {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            }
            record.release()
        }
        audioRecord = null

        silenceStartedAtMs = null
        silenceAlreadyReported = false
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
        const val SAMPLE_RATE = 44_100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val SHORT_MAX_AMPLITUDE = 32_767.0
        const val SILENCE_FLOOR_DB = -120.0
    }
}
