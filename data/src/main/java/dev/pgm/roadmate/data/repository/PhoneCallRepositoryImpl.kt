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
import java.text.Normalizer
import javax.inject.Inject

class PhoneCallRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PhoneCallRepository {

    override fun hasCallPermission(): Boolean =
        hasPermission(Manifest.permission.CALL_PHONE) && hasPermission(Manifest.permission.READ_CONTACTS)

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
     * Results are then ranked, because "llama a Ana" must not silently dial
     * "Juana": an exact name wins outright, a name whose word starts with what
     * was said comes next, and anything looser is only used when nothing
     * better matched. RoadMate asks whenever the best tier holds more than one
     * person instead of picking for the driver.
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
                if (nameIndex < 0 || numberIndex < 0) return@use
                while (cursor.moveToNext()) {
                    val contactName = cursor.getString(nameIndex) ?: continue
                    val number = cursor.getString(numberIndex) ?: continue
                    // Same person, same line, stored twice (SIM + account) is
                    // common; compare digits so "+34 600..." and "600..." are one.
                    val type = if (typeIndex >= 0) cursor.getInt(typeIndex) else -1
                    if (seenNumbers.add(number.filter { it.isDigit() || it == '+' })) {
                        matches.add(ContactMatch(contactName, number, phoneLabel(type)))
                    }
                }
            }

            if (matches.isEmpty()) return@withContext ContactLookupResult.NotFound

            // Keep only the best tier that matched at all — a weaker match is
            // never mixed in with a stronger one.
            val spoken = fold(query)
            val best = matches
                .groupBy { rank(fold(it.name), spoken) }
                .minByOrNull { it.key }
                ?.value
                ?: return@withContext ContactLookupResult.NotFound

            val distinctNames = best.map { it.name }.distinct()
            when {
                best.size == 1 -> ContactLookupResult.Found(best.first())
                // One person, several numbers: only ambiguous if the labels
                // actually differ enough to name ("el móvil o el del trabajo").
                distinctNames.size == 1 -> {
                    val namable = best.map { it.label }.filter { it != PhoneLabel.OTHER }.distinct()
                    if (namable.size >= 2) ContactLookupResult.Ambiguous(best)
                    else ContactLookupResult.Found(best.first())
                }
                else -> ContactLookupResult.Ambiguous(best)
            }
        }

    /**
     * 0 = the whole name is what was said, 1 = some word of the name starts
     * with it ("García" in "Ana García Ruiz"), 2 = it only appears somewhere
     * inside ("ana" in "Juana"). Lower is better.
     */
    private fun rank(contactName: String, spoken: String): Int = when {
        contactName == spoken -> 0
        contactName.split(' ').any { it.startsWith(spoken) } -> 1
        else -> 2
    }

    /** Lower-case and strip accents, so "garcia" matches "García". */
    private fun fold(value: String): String =
        Normalizer.normalize(value.lowercase().trim(), Normalizer.Form.NFD)
            .replace(ACCENT_MARKS, "")

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
        val ACCENT_MARKS = Regex("\\p{Mn}+")
    }
}
