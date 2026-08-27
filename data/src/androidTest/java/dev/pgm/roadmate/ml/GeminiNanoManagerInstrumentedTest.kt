package dev.pgm.roadmate.ml

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against a real device/emulator (not a Pixel with AICore support, most likely)
 * to prove GeminiNanoManager never crashes or hangs past its timeout when the
 * on-device model is unavailable — it must always resolve to some non-blank string,
 * either a real generation or FALLBACK_RESPONSE.
 */
@RunWith(AndroidJUnit4::class)
class GeminiNanoManagerInstrumentedTest {

    @Test
    fun generateResponse_neverThrowsAndAlwaysReturnsNonBlankText() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = GeminiNanoManager(context)

        val response = manager.generateResponse("¿qué distancia queda hasta el destino?")

        assertFalse("Expected a non-blank response (real or fallback)", response.isBlank())
    }
}
