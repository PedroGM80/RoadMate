package dev.pgm.roadmate.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pgm.roadmate.data.BuildConfig
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenWeatherApiKey

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @OpenWeatherApiKey
    fun provideOpenWeatherApiKey(): String = BuildConfig.OPENWEATHER_API_KEY
}
