package dev.pgm.roadmate.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.repository.MessagingRepository
import javax.inject.Inject

/**
 * Sends an SMS through the platform [SmsManager] — nothing leaves the device
 * except the message itself. Long bodies go out multipart.
 */
class MessagingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MessagingRepository {

    override fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    override fun sendSms(phoneNumber: String, body: String): Boolean {
        if (!hasSmsPermission() || phoneNumber.isBlank() || body.isBlank()) return false
        return runCatching {
            val sms = smsManager() ?: return false
            val parts = sms.divideMessage(body)
            if (parts.size <= 1) {
                sms.sendTextMessage(phoneNumber, null, body, null, null)
            } else {
                sms.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            true
        }.onFailure { Log.w(TAG, "sendSms failed", it) }.getOrDefault(false)
    }

    private fun smsManager(): SmsManager? =
        context.getSystemService(SmsManager::class.java)

    private companion object {
        const val TAG = "MessagingRepository"
    }
}
