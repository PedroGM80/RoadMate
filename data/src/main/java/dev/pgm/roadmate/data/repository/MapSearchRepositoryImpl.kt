package dev.pgm.roadmate.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.repository.MapSearchRepository
import javax.inject.Inject

/**
 * Hands the query off to whatever Maps app is installed via a geo: intent,
 * instead of RoadMate querying a places API itself — keeps the "your voice
 * and questions never leave the phone" promise intact, since the query only
 * ever travels through the Maps app the driver already has, not through
 * RoadMate's own network calls.
 */
class MapSearchRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MapSearchRepository {

    override fun searchNearby(query: String, location: Pair<Double, Double>?) {
        val encodedQuery = Uri.encode(query)
        val geoUri = if (location != null) {
            Uri.parse("geo:${location.first},${location.second}?q=$encodedQuery")
        } else {
            Uri.parse("geo:0,0?q=$encodedQuery")
        }
        val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
