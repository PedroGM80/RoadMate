package dev.pgm.roadmate.domain.repository

/**
 * Contract for sending a text message from a voice command ("dile a Ana que
 * llego"). SMS only — offline, no account, no app hand-off. Delivery is best
 * effort: [sendSms] returns whether the platform accepted it for sending.
 */
interface MessagingRepository {

    fun hasSmsPermission(): Boolean

    fun sendSms(phoneNumber: String, body: String): Boolean
}
