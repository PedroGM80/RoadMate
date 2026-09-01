package dev.pgm.roadmate.ml

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TEMP on-device tracing. MIUI/HyperOS suppresses logcat for third-party
 * apps, so the whole voice pipeline (STT result, routing, backend pick,
 * prompt, raw response, timings) is mirrored to a file we can `adb pull`
 * via `run-as`. Remove before shipping.
 */
object DebugTrace {

    @Volatile
    private var file: File? = null

    /** Idempotent: every manager that has a Context points this at the same file. */
    fun init(f: File) {
        if (file == null) file = f
    }

    fun log(line: String) {
        val f = file ?: return
        runCatching {
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            f.appendText("[$ts] $line\n")
        }
    }
}
