package dev.pgm.roadmate.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.pgm.roadmate.audio.Earcon
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.domain.repository.WakeWordRepository
import dev.pgm.roadmate.domain.repository.WeatherRepository
import dev.pgm.roadmate.domain.usecase.GenerateResponseUseCase
import dev.pgm.roadmate.domain.usecase.RecordAudioUseCase
import dev.pgm.roadmate.utils.QuestionPunctuation
import dev.pgm.roadmate.utils.SpokenText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Keeps the "oye copiloto" wake phrase listening while the app is
 * backgrounded, and answers the question that follows without bringing the UI
 * up — the driver never has to look at or touch the phone.
 *
 * Mirrors [SilenceDetectionForegroundService]: MainActivity starts this in
 * onPause() (instead of the silence service) when hands-free is available,
 * and stops it in onResume(), handing the mic back to the ViewModel's own
 * wake-word job so only one consumer holds it at a time.
 *
 * Loop: wait for one detection (which frees the wake recognizer's mic), run
 * STT + [GenerateResponseUseCase] (which speaks the answer itself), then
 * start listening for the wake phrase again.
 */
@AndroidEntryPoint
class WakeWordForegroundService : Service() {

    @Inject lateinit var wakeWordRepository: WakeWordRepository
    @Inject lateinit var recordAudioUseCase: RecordAudioUseCase
    @Inject lateinit var generateResponseUseCase: GenerateResponseUseCase
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var weatherRepository: WeatherRepository
    @Inject lateinit var speechSynthesis: SpeechSynthesisRepository

    private var scope: CoroutineScope? = null
    private var loopJob: Job? = null
    private val earcon = Earcon()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // The mic is this service's whole reason to exist. Without RECORD_AUDIO
        // it would sit in the foreground holding a notification and capturing
        // nothing.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO not granted, stopping")
            stopSelf()
            return
        }

        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        }.onFailure {
            Log.e(TAG, "Failed to start in the foreground, stopping", it)
            stopSelf()
            return
        }

        val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = serviceScope
        loopJob = serviceScope.launch { wakeWordLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        loopJob?.cancel()
        scope?.cancel()
        runCatching { earcon.release() }
        super.onDestroy()
    }

    private suspend fun wakeWordLoop() {
        val manager = getSystemService(NotificationManager::class.java)
        while (scope?.isActive == true) {
            // Collect until the first detection, then the flow cancels and the
            // wake recognizer releases the mic — leaving it free for Vosk below.
            val heard = wakeWordRepository.detections().firstOrNull()
            if (heard == null) {
                // Engine closed without ever emitting — not configured or it
                // failed. Nothing to listen with; don't spin.
                Log.w(TAG, "wake-word stream ended; stopping service")
                stopSelf()
                return
            }
            manager?.notify(NOTIFICATION_ID, buildNotification(LISTENING_TEXT))
            runCatching { earcon.start() } // audible cue, screen off
            runCatching {
                speechSynthesis.speak(SpokenText.WAKE_ACK)
                speechSynthesis.awaitDoneSpeaking()
            }
            runCatching { handleQuestion() }
                .onFailure { Log.w(TAG, "background question failed", it) }
            manager?.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private suspend fun handleQuestion() {
        val transcript = QuestionPunctuation.normalize(recordAudioUseCase.finalText())
        if (transcript.isBlank()) return
        generateResponseUseCase(buildTravelContext(transcript), transcript).collect { /* spoken by the use case */ }
    }

    private suspend fun buildTravelContext(userInput: String): TravelContext {
        val location = locationRepository.getCurrentCoordinates()
        val weather = location?.let { (lat, lon) ->
            weatherRepository.getCurrentWeatherDescription(lat, lon)
        }
        val calendar = Calendar.getInstance()
        return TravelContext(
            currentLocation = location,
            destination = null,
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
            date = calendar.time,
            userInput = userInput,
            weatherDescription = weather,
        )
    }

    private fun buildNotification(text: String = IDLE_TEXT): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RoadMate")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Escucha manos libres",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "WakeWordFgs"
        private const val CHANNEL_ID = "wake_word"
        private const val NOTIFICATION_ID = 4203
        private const val IDLE_TEXT = "Di \"oye, copiloto\" para preguntar"
        private const val LISTENING_TEXT = "Escuchando…"

        /**
         * Starting a microphone foreground service is only allowed from a
         * valid app state (Android 12+ background-start limits, tightened
         * again in 14 for the `microphone` type). MainActivity calls this from
         * onPause(), which is inside the allowance — but a pause triggered by
         * the screen locking or an incoming call can still land outside it,
         * and the system's answer to that is an exception, not a no-op.
         * RoadMate treats "couldn't keep listening in the background" as a
         * degraded mode, never a crash.
         */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, WakeWordForegroundService::class.java),
                )
            }.onFailure { Log.w(TAG, "could not start in the background", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WakeWordForegroundService::class.java)) }
        }
    }
}
