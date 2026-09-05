package dev.pgm.roadmate.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.SessionController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.MainScope
import dev.pgm.roadmate.BuildConfig
import dev.pgm.roadmate.utils.PermissionManager
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The closest thing the Car App Library has to a `@Preview`: build each
 * screen with fake dependencies and drive it through STARTED, which forces
 * the host to ask for its template. It catches what reading can't — an
 * `@RequiresCarApi` call the host rejects at runtime, an empty `ItemList`
 * the host refuses, a template type that doesn't match what the screen is
 * meant to show. Visual layout still needs the DHU (see docs/ANDROID_AUTO.md).
 *
 * `TestCarContext` and the Car App lifecycle both assert they are touched
 * from the main thread, so every body runs through [onMain].
 */
@RunWith(AndroidJUnit4::class)
class CarScreensTest {

    private val location = FakeCarLocationRepository()
    private val currentPlace = FakeCarCurrentPlaceRepository()
    private val speech = FakeCarSpeechSynthesisRepository()
    private val preferences = FakeCarPreferencesRepository()
    private val gemini = FakeCarGeminiRepository()
    private val offlineMap = FakeCarOfflineMapController()
    private val mapSearch = FakeCarMapSearchCoordinator()

    @Before
    fun grantPermissions() {
        // The granted path is the one worth rendering; without this the screens
        // only ever show the "open RoadMate on the phone" permission card.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
        ).forEach { automation.grantRuntimePermission(BuildConfig.APPLICATION_ID, it) }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T> = Result.failure(IllegalStateException("did not run"))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = runCatching(block)
        }
        return result.getOrThrow()
    }

    private fun carContext(): TestCarContext =
        TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())

    private fun permissionManager() =
        PermissionManager(ApplicationProvider.getApplicationContext())

    private fun renderer(ctx: TestCarContext) =
        CarMapRenderer(ctx, BuildConfig.MAP_STYLE_URL, location, currentPlace, MainScope())

    private fun homeScreen(ctx: TestCarContext) = HomeCarScreen(
        ctx, renderer(ctx), fakeGenerateResponseUseCase(), location, FakeCarWeatherRepository(),
        permissionManager(), speech, FakeCarPcmTranscriber(), currentPlace, mapSearch,
        preferences, gemini, offlineMap, FakeCarRoutingRepository(),
    )

    private fun navigationScreen(ctx: TestCarContext) = NavigationCarScreen(
        ctx, renderer(ctx), location, currentPlace, mapSearch,
        FakeCarRoutingRepository(), speech, offlineMap,
    )

    private fun settingsScreen(ctx: TestCarContext) = SettingsCarScreen(
        ctx, preferences, gemini, offlineMap, location,
    )

    /** Drives the screen to STARTED and returns the template it produced. */
    private fun render(screen: Screen): Template =
        ScreenController(screen).moveToState(Lifecycle.State.STARTED).screen.onGetTemplate()

    @Test
    fun homeScreen_rendersMapWithContent() = onMain {
        assertTrue(render(homeScreen(carContext())) is MapWithContentTemplate)
    }

    @Test
    fun homeScreen_templateIsStableAcrossRedraws() = onMain {
        val screen = homeScreen(carContext())
        repeat(5) { assertTrue(screen.onGetTemplate() is MapWithContentTemplate) }
    }

    @Test
    fun navigationScreen_startsAsBareNavigationTemplate() = onMain {
        assertTrue(render(navigationScreen(carContext())) is NavigationTemplate)
    }

    @Test
    fun navigationScreen_templateIsStableAcrossRedraws() = onMain {
        val screen = navigationScreen(carContext())
        repeat(5) { assertTrue(screen.onGetTemplate() is NavigationTemplate) }
    }

    @Test
    fun settingsScreen_rendersAList() = onMain {
        assertTrue(render(settingsScreen(carContext())) is ListTemplate)
    }

    @Test
    fun session_opensOnTheHomeScreen() = onMain {
        val ctx = carContext()
        val session = RoadMateSession(
            fakeGenerateResponseUseCase(), location, FakeCarWeatherRepository(), permissionManager(),
            speech, FakeCarPcmTranscriber(), currentPlace, mapSearch, preferences, gemini,
            offlineMap, FakeCarRoutingRepository(),
        )
        SessionController(session, ctx, Intent()).moveToState(Lifecycle.State.STARTED)
        assertTrue(ctx.getCarService(ScreenManager::class.java).top is HomeCarScreen)
    }
}
