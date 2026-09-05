package dev.pgm.roadmate.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import dev.pgm.roadmate.BuildConfig
import dev.pgm.roadmate.R
import dev.pgm.roadmate.domain.model.AnswerStyle
import dev.pgm.roadmate.domain.model.LocalAiCatalog
import dev.pgm.roadmate.domain.model.LocalAiStatus
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.presentation.map.OfflineMapController
import dev.pgm.roadmate.presentation.map.OfflineMapStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import kotlin.math.roundToInt

/**
 * The settings the phone's overflow menu carries, for the car: what state the
 * offline map and the local model are in, and the two switches that are safe
 * to flip while moving (answer length, hands-free).
 *
 * Downloads are wrapped in [ParkedOnlyOnClickListener]. That is not caution
 * for its own sake — the host refuses to run the action while the car is
 * moving and tells the driver to park, which is exactly what Google's
 * driver-distraction rules require of anything long-running. Reading the
 * status while driving is fine; starting a 40 MB fetch is not.
 *
 * The map download covers a box around the current fix rather than "the
 * visible area" the phone uses: there is no visible area here, because the map
 * on the car screen is drawn by the host and RoadMate never sees its viewport.
 */
class SettingsCarScreen(
    carContext: CarContext,
    private val preferences: AssistantPreferencesRepository,
    private val gemini: GeminiRepository,
    private val offlineMap: OfflineMapController,
    private val locationRepository: LocationRepository,
) : Screen(carContext) {

    private var answerStyle: AnswerStyle = AnswerStyle.DEFAULT
    private var handsFree: Boolean = true
    private var localAiStatus: LocalAiStatus = LocalAiStatus.Checking
    private var selectedModelId: String = LocalAiCatalog.recommended.id

    init {
        lifecycleScope.launch {
            answerStyle = preferences.answerStyle.first()
            handsFree = preferences.handsFreeEnabled.first()
            invalidate()
        }
        lifecycleScope.launch {
            gemini.selectedLocalAiModelId().collect {
                selectedModelId = it
                invalidate()
            }
        }
        lifecycleScope.launch { offlineMap.status.collect { invalidate() } }
        lifecycleScope.launch {
            gemini.localAiStatus().collect {
                localAiStatus = it
                invalidate()
            }
        }
        offlineMap.refresh()
    }

    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
            .addItem(offlineMapRow())
            .addItem(localAiRow())
            .addItem(modelPickerRow())
            .addItem(answerStyleRow())
            .addItem(handsFreeRow())
            .build()

        return ListTemplate.Builder()
            .setHeader(backHeader(R.string.car_settings_title))
            .setSingleList(items)
            .build()
    }

    /**
     * Status only. Deleting the downloaded regions used to be this row's own
     * click listener, which meant one tap on a line that reads as information
     * threw the maps away — and being parked, the only thing standing in front
     * of it, is the normal case. Both actions now live behind
     * [offlineMapScreen] where they are named for what they do.
     */
    private fun offlineMapRow(): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_settings_offline_map))
        .addText(offlineMapText(offlineMap.status.value))
        .setImage(carIcon(R.drawable.lucide_ic_map))
        .setBrowsable(true)
        .setOnClickListener { screenManager.push(offlineMapScreen()) }
        .build()

    private fun offlineMapScreen(): Screen = object : Screen(carContext) {
        override fun onGetTemplate(): Template {
            val status = offlineMap.status.value
            val list = ItemList.Builder()
                .addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.car_settings_map_download))
                        .addText(offlineMapText(status))
                        .setImage(carIcon(R.drawable.lucide_ic_download))
                        .setOnClickListener(
                            ParkedOnlyOnClickListener.create { downloadAroundHere() }
                        )
                        .build()
                )
            if (status is OfflineMapStatus.Ready) {
                list.addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.car_settings_map_delete))
                        .addText(carContext.getString(R.string.car_settings_map_delete_note))
                        .setImage(carIcon(R.drawable.lucide_ic_square))
                        .setOnClickListener(
                            ParkedOnlyOnClickListener.create {
                                offlineMap.deleteAll()
                                screenManager.pop()
                            }
                        )
                        .build()
                )
            }
            return ListTemplate.Builder()
                .setHeader(backHeader(R.string.car_settings_offline_map))
                .setSingleList(list.build())
                .build()
        }
    }

    private fun offlineMapText(status: OfflineMapStatus): String = when (status) {
        is OfflineMapStatus.Unknown -> carContext.getString(R.string.car_settings_checking)
        is OfflineMapStatus.Idle -> carContext.getString(R.string.car_settings_map_idle)
        is OfflineMapStatus.Downloading -> carContext.getString(
            R.string.map_offline_downloading,
            (status.progress * 100).roundToInt(),
        )
        is OfflineMapStatus.Ready -> carContext.getString(R.string.car_settings_map_ready)
        is OfflineMapStatus.Failed -> carContext.getString(status.messageRes)
    }

    private fun localAiRow(): Row {
        val status = localAiStatus
        val builder = Row.Builder()
            .setTitle(carContext.getString(R.string.car_settings_local_ai))
            .addText(localAiText(status))
            .setImage(carIcon(R.drawable.lucide_ic_cloud_off))

        if (status is LocalAiStatus.ModelDownloadable ||
            status is LocalAiStatus.DownloadFailed ||
            status is LocalAiStatus.WaitingForWifi
        ) {
            builder.setOnClickListener(
                ParkedOnlyOnClickListener.create {
                    lifecycleScope.launch { gemini.requestLocalAiModelDownload() }
                }
            )
        }
        return builder.build()
    }

    private fun localAiText(status: LocalAiStatus): String = when (status) {
        is LocalAiStatus.Checking -> carContext.getString(R.string.car_settings_checking)
        is LocalAiStatus.ReadyAicore -> carContext.getString(R.string.car_settings_ai_aicore)
        is LocalAiStatus.ReadyLocalModel -> carContext.getString(R.string.car_settings_ai_ready)
        is LocalAiStatus.ModelDownloadable -> carContext.getString(R.string.car_settings_ai_downloadable)
        is LocalAiStatus.Downloading -> carContext.getString(
            R.string.car_settings_ai_downloading,
            (status.progress * 100).roundToInt(),
        )
        is LocalAiStatus.WaitingForWifi -> carContext.getString(R.string.car_settings_ai_wifi)
        is LocalAiStatus.DownloadFailed -> status.message
        is LocalAiStatus.Unavailable -> carContext.getString(R.string.car_settings_ai_unavailable)
    }

    /**
     * Which downloadable model the local AI runs, mirroring the picker in the
     * phone's overflow menu. Browsable rather than a cycling row: the catalog
     * is longer than two entries and each one carries a size and a trade-off
     * the driver should read before committing to a download.
     */
    private fun modelPickerRow(): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_settings_model))
        .addText(gemini.localAiModels.firstOrNull { it.id == selectedModelId }?.name.orEmpty())
        .setImage(carIcon(R.drawable.lucide_ic_settings))
        .setBrowsable(true)
        .setOnClickListener { screenManager.push(modelPickerScreen()) }
        .build()

    private fun modelPickerScreen(): Screen = object : Screen(carContext) {
        override fun onGetTemplate(): Template {
            val list = ItemList.Builder()
            gemini.localAiModels.forEach { model ->
                val mark = if (model.id == selectedModelId) "✓ " else ""
                list.addItem(
                    Row.Builder()
                        .setTitle(mark + model.name + sizeSuffix(model.approxSize))
                        .addText(model.note)
                        // Switching model means fetching a new half-gigabyte
                        // file, so it is parked-only for the same reason the
                        // downloads above are.
                        .setOnClickListener(
                            ParkedOnlyOnClickListener.create {
                                lifecycleScope.launch {
                                    gemini.selectLocalAiModel(model.id)
                                    gemini.requestLocalAiModelDownload()
                                }
                                screenManager.pop()
                            }
                        )
                        .build()
                )
            }
            return ListTemplate.Builder()
                .setHeader(backHeader(R.string.car_settings_model))
                .setSingleList(list.build())
                .build()
        }

        private fun sizeSuffix(size: String) = if (size.isBlank()) "" else " · $size"
    }

    private fun answerStyleRow(): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_settings_answer_style))
        .addText(carContext.getString(answerStyleLabel(answerStyle)))
        .setImage(carIcon(R.drawable.lucide_ic_moon_star))
        .setOnClickListener {
            val next = AnswerStyle.entries[(answerStyle.ordinal + 1) % AnswerStyle.entries.size]
            answerStyle = next
            invalidate()
            lifecycleScope.launch { preferences.setAnswerStyle(next) }
        }
        .build()

    private fun answerStyleLabel(style: AnswerStyle): Int = when (style) {
        AnswerStyle.BRIEF -> R.string.car_settings_style_brief
        AnswerStyle.NORMAL -> R.string.car_settings_style_normal
        AnswerStyle.DETAILED -> R.string.car_settings_style_detailed
    }

    private fun handsFreeRow(): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_settings_hands_free))
        .addText(
            carContext.getString(
                if (handsFree) R.string.car_settings_on else R.string.car_settings_off
            )
        )
        .setImage(carIcon(R.drawable.lucide_ic_mic))
        .setOnClickListener {
            handsFree = !handsFree
            invalidate()
            lifecycleScope.launch { preferences.setHandsFreeEnabled(handsFree) }
        }
        .build()

    /** Title + a back action, the shape every screen here uses. */
    private fun backHeader(titleRes: Int): Header = Header.Builder()
        .setTitle(carContext.getString(titleRes))
        .setStartHeaderAction(Action.BACK)
        .build()

    /**
     * A ~10 km box around the current fix. The phone downloads whatever the
     * driver has on screen; there is no screen to read here, so this is the
     * useful approximation of "where I am now".
     */
    private fun downloadAroundHere() {
        val loc = locationRepository.location.value ?: return
        val lat = loc.latitude
        val lon = loc.longitude
        val bounds = LatLngBounds.Builder()
            .include(LatLng(lat + BOX_DEGREES, lon + BOX_DEGREES))
            .include(LatLng(lat - BOX_DEGREES, lon - BOX_DEGREES))
            .build()
        offlineMap.download(BuildConfig.MAP_STYLE_URL, bounds, PIXEL_RATIO)
    }

    private companion object {
        /** ~5 km each way at Spanish latitudes. */
        const val BOX_DEGREES = 0.045

        /** Car screens are around 1x-2x; 2f keeps the tiles sharp on both. */
        const val PIXEL_RATIO = 2f
    }
}
