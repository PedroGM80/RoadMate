package dev.pgm.roadmate.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pgm.roadmate.domain.repository.ReverseGeocodeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reverse geocoding via the Android platform [Geocoder] — the OS's own
 * service, not a Google SDK call from the app. It carries offline data on
 * many devices/regions; where it doesn't it returns nothing and the chip
 * shows coordinates. Never throws.
 */
@Singleton
class AndroidReverseGeocoder @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReverseGeocodeRepository {

    private val geocoder: Geocoder? by lazy {
        if (Geocoder.isPresent()) Geocoder(context, Locale("es", "ES")) else null
    }

    override suspend fun describe(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val g = geocoder ?: return@withContext null
        val address = runCatching {
            @Suppress("DEPRECATION")
            g.getFromLocation(lat, lon, 1)
        }.getOrNull()?.firstOrNull() ?: return@withContext null
        format(address)
    }

    private fun format(a: Address): String? {
        val street = a.thoroughfare?.trim()?.takeIf { it.isNotEmpty() }?.let { s ->
            a.subThoroughfare?.trim()?.takeIf { it.isNotEmpty() }?.let { "$s $it" } ?: s
        }
        val town = (a.locality ?: a.subAdminArea)?.trim()?.takeIf { it.isNotEmpty() }
        val province = a.adminArea?.trim()?.takeIf { it.isNotEmpty() }

        val head = listOfNotNull(street, town).joinToString(", ")
        return when {
            head.isBlank() -> province
            province != null && !province.equals(town, ignoreCase = true) -> "$head · $province"
            else -> head
        }?.takeIf { it.isNotBlank() }
    }
}
