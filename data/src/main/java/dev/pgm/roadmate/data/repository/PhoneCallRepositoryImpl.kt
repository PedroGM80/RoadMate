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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PhoneCallRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PhoneCallRepository {

    override fun hasCallPermission(): Boolean =
        hasPermission(Manifest.permission.CALL_PHONE) && hasPermission(Manifest.permission.READ_CONTACTS)

    override suspend fun findContactByName(name: String): ContactLookupResult =
        withContext(Dispatchers.IO) {
            if (!hasPermission(Manifest.permission.READ_CONTACTS)) return@withContext ContactLookupResult.NotFound

            val matches = mutableListOf<ContactMatch>()
            val seenNumbers = mutableSetOf<String>()
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
            )

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                while (cursor.moveToNext()) {
                    val contactName = cursor.getString(nameIndex) ?: continue
                    val number = cursor.getString(numberIndex) ?: continue
                    if (seenNumbers.add(number)) {
                        matches.add(ContactMatch(contactName, number, phoneLabel(cursor.getInt(typeIndex))))
                    }
                }
            }

            val distinctNames = matches.map { it.name }.distinct()
            when {
                matches.isEmpty() -> ContactLookupResult.NotFound
                matches.size == 1 -> ContactLookupResult.Found(matches.first())
                // One person, several numbers: only ambiguous if the labels
                // actually differ enough to name ("el móvil o el del trabajo").
                distinctNames.size == 1 -> {
                    val namable = matches.map { it.label }.filter { it != PhoneLabel.OTHER }.distinct()
                    if (namable.size >= 2) ContactLookupResult.Ambiguous(matches)
                    else ContactLookupResult.Found(matches.first())
                }
                else -> ContactLookupResult.Ambiguous(matches)
            }
        }

    override fun placeCall(phoneNumber: String) {
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // A phone with no dialer is exotic but possible — don't crash the loop.
        runCatching { context.startActivity(callIntent) }
            .onFailure { Log.w(TAG, "No activity to place the call", it) }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

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
