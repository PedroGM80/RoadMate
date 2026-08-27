package dev.pgm.roadmate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.pgm.roadmate.domain.repository.SilenceDetectionRepository
import dev.pgm.roadmate.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the rest-reminder silence monitor alive while the app is backgrounded.
 *
 * Plain AudioRecord access (what RoadMateViewModel's monitor uses while the app
 * is foregrounded, via the same SilenceDetectionRepository) is generally denied
 * to background processes since Android 9 — a foreground service is the actual
 * exemption mechanism, not WorkManager (see the deleted SilenceDetectionService
 * worker this replaces). MainActivity starts this in onPause() and stops it in
 * onResume(), handing the mic back to the ViewModel's own monitor so only one
 * consumer holds it at a time.
 */
@AndroidEntryPoint
class SilenceDetectionForegroundService : Service() {

    @Inject
    lateinit var silenceDetectionRepository: SilenceDetectionRepository

    private var scope: CoroutineScope? = null
    private var monitoringJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // The type passed here must match android:foregroundServiceType="microphone"
        // in the manifest — targetSdk 37 throws InvalidForegroundServiceTypeException
        // otherwise (caught this by actually running the service, not just reading docs).
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(monitoring = true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        }.onFailure {
            Log.e(TAG, "Failed to start in the foreground, stopping", it)
            stopSelf()
        }

        val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = serviceScope
        monitoringJob = serviceScope.launch {
            silenceDetectionRepository
                .observeSilence(Constants.REST_REMINDER_SILENCE_MS, Constants.SILENCE_THRESHOLD_DB)
                .collect { onSilenceDetected() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitoringJob?.cancel()
        scope?.cancel()
        super.onDestroy()
    }

    private fun onSilenceDetected() {
        // TODO: route this through GenerateResponseUseCase + TTS once background
        // Gemini/TTS orchestration is wired up; for now, just surface it visually.
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(monitoring = false))
    }

    private fun buildNotification(monitoring: Boolean): Notification {
        val text = if (monitoring) {
            "Escuchando en segundo plano por si necesitas un descanso."
        } else {
            "Llevas un rato en silencio. ¿Necesitas descansar?"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RoadMate")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SilenceDetectionForegroundService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SilenceDetectionForegroundService::class.java))
        }
    }
}
