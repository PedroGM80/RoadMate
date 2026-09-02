package dev.pgm.roadmate.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pgm.roadmate.data.repository.AssistantPreferencesRepositoryImpl
import dev.pgm.roadmate.data.repository.GeminiRepositoryImpl
import dev.pgm.roadmate.data.repository.GreetingRepositoryImpl
import dev.pgm.roadmate.data.repository.LocationRepositoryImpl
import dev.pgm.roadmate.data.repository.MediaRepositoryImpl
import dev.pgm.roadmate.data.repository.MemoryRepositoryImpl
import dev.pgm.roadmate.data.repository.MessagingRepositoryImpl
import dev.pgm.roadmate.data.repository.OnboardingRepositoryImpl
import dev.pgm.roadmate.data.repository.PhoneCallRepositoryImpl
import dev.pgm.roadmate.data.repository.SilenceDetectionRepositoryImpl
import dev.pgm.roadmate.data.repository.SpeechRecognitionRepositoryImpl
import dev.pgm.roadmate.data.repository.SpeechSynthesisRepositoryImpl
import dev.pgm.roadmate.data.repository.WakeWordRepositoryImpl
import dev.pgm.roadmate.data.repository.WeatherRepositoryImpl
import dev.pgm.roadmate.routing.BRouterRouter
import dev.pgm.roadmate.domain.repository.AssistantPreferencesRepository
import dev.pgm.roadmate.domain.repository.GeminiRepository
import dev.pgm.roadmate.domain.repository.GreetingRepository
import dev.pgm.roadmate.domain.repository.LocationRepository
import dev.pgm.roadmate.domain.repository.MediaRepository
import dev.pgm.roadmate.domain.repository.MemoryRepository
import dev.pgm.roadmate.domain.repository.MessagingRepository
import dev.pgm.roadmate.domain.repository.RoutingRepository
import dev.pgm.roadmate.domain.repository.OnboardingRepository
import dev.pgm.roadmate.domain.repository.PhoneCallRepository
import dev.pgm.roadmate.domain.repository.SilenceDetectionRepository
import dev.pgm.roadmate.domain.repository.SpeechRecognitionRepository
import dev.pgm.roadmate.domain.repository.SpeechSynthesisRepository
import dev.pgm.roadmate.domain.repository.WakeWordRepository
import dev.pgm.roadmate.domain.repository.WeatherRepository

/**
 * Binds each domain/repository contract to its data-layer implementation —
 * the only place in the app that knows both sides exist.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    abstract fun bindGeminiRepository(impl: GeminiRepositoryImpl): GeminiRepository

    @Binds
    abstract fun bindSpeechSynthesisRepository(impl: SpeechSynthesisRepositoryImpl): SpeechSynthesisRepository

    @Binds
    abstract fun bindSpeechRecognitionRepository(impl: SpeechRecognitionRepositoryImpl): SpeechRecognitionRepository

    @Binds
    abstract fun bindSilenceDetectionRepository(impl: SilenceDetectionRepositoryImpl): SilenceDetectionRepository

    @Binds
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    @Binds
    abstract fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository

    @Binds
    abstract fun bindPhoneCallRepository(impl: PhoneCallRepositoryImpl): PhoneCallRepository

    @Binds
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    abstract fun bindMemoryRepository(impl: MemoryRepositoryImpl): MemoryRepository

    @Binds
    abstract fun bindGreetingRepository(impl: GreetingRepositoryImpl): GreetingRepository

    @Binds
    abstract fun bindAssistantPreferencesRepository(
        impl: AssistantPreferencesRepositoryImpl
    ): AssistantPreferencesRepository

    @Binds
    abstract fun bindWakeWordRepository(impl: WakeWordRepositoryImpl): WakeWordRepository

    @Binds
    abstract fun bindRoutingRepository(impl: BRouterRouter): RoutingRepository

    @Binds
    abstract fun bindMessagingRepository(impl: MessagingRepositoryImpl): MessagingRepository
}
