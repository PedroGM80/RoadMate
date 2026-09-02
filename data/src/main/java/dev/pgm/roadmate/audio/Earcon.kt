package dev.pgm.roadmate.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * The short two-note blip on mic start/stop. Synthesised in code (no bundled
 * asset) and played through the sonification stream so it follows the
 * device's notification volume / Do-Not-Disturb. Rising for "start",
 * falling for "stop".
 */
class Earcon {

    private var lastTrack: AudioTrack? = null

    /** Rising blip — "listening". */
    fun start() = play(firstHz = 620.0, secondHz = 930.0)

    /** Falling blip — "stopped". */
    fun stop() = play(firstHz = 880.0, secondHz = 560.0)

    private fun play(firstHz: Double, secondHz: Double) {
        val samples = buildBlip(firstHz, secondHz)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        runCatching {
            track.write(samples, 0, samples.size)
            track.play()
        }
        lastTrack?.let { runCatching { it.release() } }
        lastTrack = track
    }

    fun release() {
        lastTrack?.let { runCatching { it.release() } }
        lastTrack = null
    }

    private fun buildBlip(firstHz: Double, secondHz: Double): ShortArray {
        val noteFrames = SAMPLE_RATE * NOTE_MS / 1000
        val out = ShortArray(noteFrames * 2)
        writeNote(out, 0, noteFrames, firstHz)
        writeNote(out, noteFrames, noteFrames, secondHz)
        return out
    }

    private fun writeNote(out: ShortArray, offset: Int, frames: Int, hz: Double) {
        val fade = frames / 6 // click-free attack/release
        for (i in 0 until frames) {
            val env = when {
                i < fade -> i.toDouble() / fade
                i > frames - fade -> (frames - i).toDouble() / fade
                else -> 1.0
            }
            val v = sin(2.0 * PI * hz * i / SAMPLE_RATE) * env * AMPLITUDE
            out[offset + i] = (v * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val NOTE_MS = 70
        const val AMPLITUDE = 0.35
    }
}
