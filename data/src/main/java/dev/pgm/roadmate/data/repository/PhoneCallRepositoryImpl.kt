package dev.pgm.roadmate.data.repository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.model.ContactLookupResult
import dev.pgm.roadmate.domain.model.ContactMatch
import dev.pgm.roadmate.domain.model.PhoneLabel
import dev.pgm.roadmate.domain.repository.PhoneCallRepository
import dev.pgm.roadmate.utils.ContactMatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PhoneCallRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PhoneCallRepository {

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun hasCallPermission(): Boolean =
        hasPermission(Manifest.permission.CALL_PHONE) && hasPermission(Manifest.permission.READ_CONTACTS)

    override fun placeCall(phoneNumber: String) {
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // A phone with no dialer is exotic but possible — don't crash the loop.
        runCatching { context.startActivity(callIntent) }
            .onFailure { Log.w(TAG, "No activity to place the call", it) }
    }

    /**
     * Looks the name up through [ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI]
     * rather than a hand-built `DISPLAY_NAME LIKE '%name%'`.
     *
     * Two reasons. The old query pasted the transcript straight into a LIKE
     * pattern, so a stray `%` or `_` from a mis-transcription silently became
     * a wildcard — and since a single row is dialled without confirmation,
     * "everyone" matching and the first row winning is not a harmless bug.
     * And CONTENT_FILTER_URI is the platform's own name search: it matches on
     * name *tokens*, handles accents and alternative scripts, and knows about
     * nicknames — so "llama a García" finds "Ana García" the way a person
     * would expect, where `%garcía%` was both looser and dumber.
     *
     * Choosing between what comes back is [ContactMatching]'s job — that rule
     * decides whether RoadMate dials on its own or asks, so it lives in
     * `:domain` where it can be unit-tested without a ContentResolver.
     */
    override suspend fun findContactByName(name: String): ContactLookupResult =
        withContext(Dispatchers.IO) {
            if (!hasPermission(Manifest.permission.READ_CONTACTS)) return@withContext ContactLookupResult.NotFound
            val query = name.trim()
            if (query.isBlank()) return@withContext ContactLookupResult.NotFound

            val matches = mutableListOf<ContactMatch>()
            val seenNumbers = mutableSetOf<String>()
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
            )
            val uri = Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                Uri.encode(query),
            )

            runCatching {
                context.contentResolver.query(uri, projection, null, null, null)
            }.getOrNull()?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                if (nameIndex < 0 || numberIndex < 0) return@use
                while (cursor.moveToNext()) {
                    val contactName = cursor.getString(nameIndex) ?: continue
                    val number = cursor.getString(numberIndex) ?: continue
                    // Same person, same line, stored twice (SIM + account) is
                    // common; compare digits so "+34 600..." and "600..." are one.
                    val type = if (typeIndex >= 0) cursor.getInt(typeIndex) else -1
                    // TYPE_CUSTOM carries the contact's own word for this
                    // number ("Coche"). Without it every custom number was an
                    // unnameable "otro".
                    val custom = if (labelIndex >= 0) cursor.getString(labelIndex) else null
                    if (seenNumbers.add(ContactMatching.normalizeNumber(number))) {
                        matches.add(ContactMatch(contactName, number, phoneLabel(type), custom))
                    }
                }
            }

            // Who to dial, and whether RoadMate is allowed to decide that on
            // its own, is ContactMatching's call — see there for the rule.
            ContactMatching.resolve(matches, query)
        }

    private fun phoneLabel(type: Int): PhoneLabel = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> PhoneLabel.MOBILE
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK,
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE -> PhoneLabel.WORK
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> PhoneLabel.HOME
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> PhoneLabel.MAIN
        else -> PhoneLabel.OTHER
    }

    private companion object {
        const val TAG = "PhoneCallRepository"
    }
}
