package dev.pgm.roadmate.ml

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * TEMP on-device tracing. MIUI/HyperOS suppresses logcat for third-party
 * apps, so the whole voice pipeline (STT result, routing, backend pick,
 * prompt, raw response, timings) is mirrored to a file we can `adb pull`
 * via `run-as`. Remove before shipping.
 *
 * Writes go to one background thread: [log] is called from the main thread on
 * the answer's critical path (TTS, the ViewModel), and an `appendText` there
 * is a disk write inside the frame — exactly the jank the tracing is meant to
 * measure. The file is also capped, so a long trip can't fill the device.
 */
object DebugTrace {

    @Volatile
    private var file: File? = null

    private val writer by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "roadmate-trace").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }
    }

    /** Idempotent: every manager that has a Context points this at the same file. */
    fun init(f: File) {
        if (file == null) file = f
    }

    fun log(line: String) {
        val f = file ?: return
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        runCatching {
            writer.execute {
                runCatching {
                    if (f.length() > MAX_BYTES) f.writeText("[$ts] --- trace truncated ---\n")
                    f.appendText("[$ts] $line\n")
                }
            }
        }
    }

    private const val MAX_BYTES = 4L * 1024 * 1024
}
