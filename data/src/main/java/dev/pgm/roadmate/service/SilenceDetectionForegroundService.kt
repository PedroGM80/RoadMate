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
import dev.pgm.roadmate.domain.model.TravelContext
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.usecase.DetectSilenceUseCase
import dev.pgm.roadmate.utils.SpokenText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Keeps the rest-reminder silence monitor alive — and actually speaking —
 * while the app is backgrounded.
 *
 * Plain AudioRecord access (what RoadMateViewModel's monitor uses while the app
 * is foregrounded, via the same DetectSilenceUseCase) is generally denied to
 * background processes since Android 9 — a foreground service is the actual
 * exemption mechanism, not WorkManager (see the deleted SilenceDetectionService
 * worker this replaces). MainActivity starts this in onPause() and stops it in
 * onResume(), handing the mic back to the ViewModel's own monitor so only one
 * consumer holds it at a time.
 *
 * On silence, routes through DetectSilenceUseCase.triggerRestPrompt() — the
 * same path RoadMateViewModel uses in the foreground — which builds the
 * prompt, asks Gemini Nano, and speaks the response via
 * SpeechSynthesisRepository. Earlier this only posted a silent notification;
 * now the rest reminder is actually audible even with the app backgrounded.
 */
@AndroidEntryPoint
class SilenceDetectionForegroundService : Service() {

    @Inject
    lateinit var detectSilenceUseCase: DetectSilenceUseCase

    @Inject
    lateinit var locationRepository: LocationRepository

    private var scope: CoroutineScope? = null
    private var monitoringJob: Job? = null

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

        // The type passed here must match android:foregroundServiceType="microphone"
        // in the manifest — targetSdk 37 throws InvalidForegroundServiceTypeException
        // otherwise (caught this by actually running the service, not just reading docs).
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        }.onFailure {
            Log.e(TAG, "Failed to start in the foreground, stopping", it)
            stopSelf()
            // Without this the service went on to open the mic and monitor
            // anyway, with no foreground notification backing it.
            return
        }

        val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = serviceScope
        monitoringJob = serviceScope.launch {
            detectSilenceUseCase.observeSilence().collect { onSilenceDetected() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitoringJob?.cancel()
        scope?.cancel()
        super.onDestroy()
    }

    private suspend fun onSilenceDetected() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(SpokenText.REST_NUDGE))

        val travelContext = buildTravelContext()
        detectSilenceUseCase.triggerRestPrompt(travelContext).collect { response ->
            // GenerateResponseUseCase already spoke `response` via
            // SpeechSynthesisRepository — this just keeps the notification in
            // sync so it doesn't stay stuck on the pre-response text.
            manager?.notify(NOTIFICATION_ID, buildNotification(response))
        }
    }

    private suspend fun buildTravelContext(): TravelContext {
        val location = locationRepository.getCurrentCoordinates()
        val calendar = Calendar.getInstance()
        return TravelContext(
            currentLocation = location,
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            date = calendar.time,
            userInput = ""
        )
    }

    private fun buildNotification(text: String = SpokenText.REST_MONITOR): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RoadMate")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monitor de descanso",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "SilenceDetectionFgs"
        private const val CHANNEL_ID = "silence_detection"
        private const val NOTIFICATION_ID = 4202

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
                    Intent(context, SilenceDetectionForegroundService::class.java),
                )
            }.onFailure { Log.w(TAG, "could not start in the background", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, SilenceDetectionForegroundService::class.java)) }
        }
    }
}
